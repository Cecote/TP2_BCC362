package main.java.com.distributed;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class StorageServer {
    private static final long HEARTBEAT_TIMEOUT = 10000;
    private int requestCounter = 0;
    private boolean iHaveRequest = false;
    //private int timeStampOrder
    private int id;
    private boolean myrequest = false;
    private boolean primaryAndWriter = false;
    private int memberIdAct;
    private int memberRequestId;
    private int memberRequestTimestamp;
    private Socket clientSocketGlobal;
    private int primaryClusterStorage;
    private boolean iAmPrimary = false;
    private long timestamp;
    private int port;
    private List<Map.Entry<String, Integer>> otherStorageServers;
    private int counter = 0; // variável global para incrementar

    // Timestamps dos outros servidores
    private Map<String, Long> serverTimestamps = new HashMap<>();
    private Map<String, Long> serverTimestampsPing = new HashMap<>();

    public StorageServer(int id, int port, List<Map.Entry<String, Integer>> otherStorageServers) {
        this.id = id;
        this.port = port;
        this.otherStorageServers = otherStorageServers;
        //this.timestamp = System.currentTimeMillis(); // Gera timestamp ao iniciar
        this.timestamp = new Random().nextInt(1000);
    }

    public void run() {
        // Iniciar a thread para receber mensagens e requisições (assíncrono)
        new Thread(this::receiveMessagesFromClusterAndRequests).start();

        try {
            Thread.sleep(10000); // Tempo de espera para garantir a comunicação (ajustável)
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Adicionar aqui as chamadas para enviar e monitorar heartbeats
        sendHeartbeats();  // Começa a enviar heartbeats para os outros storages
        monitorHeartbeats();  // Começa a monitorar os heartbeats recebidos

        // Enviar o timestamp para os outros servidores em paralelo
        shareTimestampWithCluster();

        // Aguarda um tempo para garantir que todos os timestamps foram recebidos
        try {
            Thread.sleep(10000); // Tempo de espera para garantir a comunicação (ajustável)
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Verifica se este servidor tem o menor timestamp
        checkIfPrimary();
    }

    // Envia o timestamp para os outros servidores do cluster
    private void shareTimestampWithCluster() {
        for (Map.Entry<String, Integer> server : otherStorageServers) {
            new Thread(() -> {
                try (Socket socket = new Socket(server.getKey(), server.getValue())) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println("TIMESTAMP " + port + " " + timestamp);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start(); // Cada envio é feito em uma thread separada
        }
    }

    // Recebe mensagens (incluindo timestamps) dos outros servidores em uma thread dedicada
    private void receiveMessagesFromClusterAndRequests() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String message = in.readLine();

                if (message != null && message.startsWith("TIMESTAMP")) {
                    String[] parts = message.split(" ");
                    String serverPort = parts[1];
                    long receivedTimestamp = Long.parseLong(parts[2]);
                    serverTimestamps.put(serverPort, receivedTimestamp);
                    System.out.println("Recebi timestamp de " + serverPort + ": " + receivedTimestamp);
                  //  System.out.println("Timestamps antes de tudo: " + serverTimestamps);
                } else if (message != null && message.startsWith("WRITE1")) {
                    iHaveRequest = true;
                    requestCounter++;
                    myrequest = true;
                    clientSocketGlobal = socket;
                    if(iAmPrimary) {
                        this.primaryAndWriter = true;
                    }
                    String[] parts = message.split(":");
                    this.memberIdAct = Integer.parseInt(parts[2]);
                    this.memberRequestId = Integer.parseInt(parts[1]);
                    this.memberRequestTimestamp = Integer.parseInt(parts[3]);
                    System.out.println("Recebi o request nº: " + memberRequestId);
                    System.out.println("Request feito pelo membro: " + memberIdAct);
                    handleWriteRequest(message);
                } else if (message != null && message.startsWith("HEARTBEAT")) {
                    // Atualizar o timestamp do servidor que enviou o heartbeat
                    String[] parts = message.split(" ");
                    String serverPort = parts[1];
                    serverTimestampsPing.put(serverPort, System.currentTimeMillis());  // Atualiza o último heartbeat recebido
                    //System.out.println("Recebi heartbeat de " + serverPort);

                } else if (message != null && message.startsWith("NEW_VALUE")) {
                    String[] parts = message.split(" ");
                    counter = Integer.parseInt(parts[1]);
                    System.out.println("Novo valor: " + counter);

                    if(myrequest && clientSocketGlobal != null) {
                        iHaveRequest = false;
                        try (PrintWriter out = new PrintWriter(clientSocketGlobal.getOutputStream(), true)) {
                            out.println("COMMITED");
                            System.out.println("Resposta COMMITED enviada para o solicitante.");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        myrequest = false;
                    } else if(myrequest && clientSocketGlobal == null) {
                        System.out.println("Erro: socket do cliente não foi encontrado.");
                    }
                    if(!iHaveRequest && requestCounter == 3 && !iAmPrimary){
                        System.exit(0);
                    }
                } else if (message != null && message.startsWith("WRITE2")) {
                    String[] parts = message.split(":");
                    this.memberIdAct = Integer.parseInt(parts[2]);
                    System.out.println("Request feito pelo membro: " + memberIdAct);
                    handleWriteRequest(message);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Determina se este servidor é o primário com base nos timestamps
    private void checkIfPrimary() {
        // Adiciona o próprio timestamp
        serverTimestamps.put(Integer.toString(port), timestamp);

      //  System.out.println("Timestamps antes: " + serverTimestamps);

        // Verifica o menor timestamp
        String primaryServer = serverTimestamps.entrySet()
                .stream()
                .min(Map.Entry.comparingByValue())
                .get().getKey();
     //   System.out.println("Porta: "+ port + " Timestamp: " + timestamp);
    //    System.out.println("Primário: " + primaryServer);
     //   System.out.println("Timestamps depois: " + serverTimestamps);
        if (primaryServer.equals(Integer.toString(port))) {
            iAmPrimary = true;
            System.out.println("Eu sou o primário!");
        } else {
            iAmPrimary = false;
            System.out.println("Eu NÃO sou o primário!");
            this.primaryClusterStorage = Integer.parseInt(primaryServer);
            System.out.println("O Primário é: " + primaryClusterStorage);
        }
    }

    // Lida com a operação de escrita
    private void handleWriteRequest(String message) {
        if (iAmPrimary) {
            // Incrementa o valor

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            counter++;
            System.out.println("Novo valor: " + counter);

            // Propaga o novo valor para os outros servidores
            propagateNewValueToCluster(counter);

            //Retorno para o commited para o membro do clustersync no caso de eu ser o primário e ter recebido a requisição direto dele
            if(myrequest && clientSocketGlobal != null && primaryAndWriter) {
                try (PrintWriter out = new PrintWriter(clientSocketGlobal.getOutputStream(), true)) {
                    out.println("COMMITED");
                    System.out.println("Resposta COMMITED enviada para o solicitante.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                myrequest = false;
                primaryAndWriter = false;
            } else if(myrequest && clientSocketGlobal == null) {
                System.out.println("Erro: socket do cliente não foi encontrado.");
            }

            //System.out.println("Retorno ao Membro: "  + " porta: "  + " feito com sucesso!");
        } else {
            // Repassa a requisição para o primário
            forwardRequestToPrimary(message);
        }
    }

    // Propaga o novo valor para os outros servidores
    private void propagateNewValueToCluster(int newValue) {
        for (Map.Entry<String, Integer> server : otherStorageServers) {
            new Thread(() -> {
                try (Socket socket = new Socket(server.getKey(), server.getValue())) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println("NEW_VALUE " + newValue);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start(); // Envia o valor atualizado em threads paralelas
        }
    }

    // Repassa a requisição ao primário
    private void forwardRequestToPrimary(String message) {

        for (Map.Entry<String, Integer> server : otherStorageServers) {
            if (server.getValue() == primaryClusterStorage) {
                new Thread(() -> {
                    try (Socket socket = new Socket(server.getKey(), server.getValue())) {
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        out.println("WRITE2:" + memberRequestId + ":" + memberIdAct + ":" + memberRequestTimestamp);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();
                break;
            }
        }
    }

    private void sendHeartbeats() {
        new Thread(() -> {
            while (true) {
                try {
                    for (Map.Entry<String, Integer> server : otherStorageServers) {
                        try (Socket socket = new Socket(server.getKey(), server.getValue())) {
                            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                            out.println("HEARTBEAT " + port);
                        } catch (IOException e) {
                            //System.out.println("OPA1");
                            requestCounter = 10;
                        }
                    }
                    Thread.sleep(1000);  // Envia heartbeats a cada 5 segundos (pode ajustar o tempo)
                } catch (InterruptedException e) {
                   System.out.println("OPA2");
                    e.printStackTrace();
                }
            }
        }).start();
    }


    private void monitorHeartbeats() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(3000);  // Exemplo de intervalo de verificação de 10 segundos
                    long currentTime = System.currentTimeMillis();
                    for (Map.Entry<String, Long> entry : serverTimestampsPing.entrySet()) {
                        if (currentTime - entry.getValue() > HEARTBEAT_TIMEOUT) {
                            System.out.println("Falha detectada no servidor: " + entry.getKey());
                            // Marcar esse storage como inativo
                            removeEntryByValue(Integer.parseInt(entry.getKey()));
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void removeEntryByValue(int valueToRemove) {
        Iterator<Map.Entry<String, Integer>> iterator = otherStorageServers.iterator();

        // Percorre a lista e remove os itens cujo valor é igual a valueToRemove
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            if (entry.getValue().equals(valueToRemove)) {
                iterator.remove(); // Remover a entrada
            }
        }
    }


    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Uso: java StorageServer <id> <porta> <portas de outros servidores...>");
            return;
        }

        int id = Integer.parseInt(args[0]);
        int port = Integer.parseInt(args[1]);
        List<Map.Entry<String, Integer>> otherServers = new ArrayList<>();

        for (int i = 2; i < args.length; i += 2) {
            String name = args[i];
            int memberPort = Integer.parseInt(args[i + 1]);
            otherServers.add(new AbstractMap.SimpleEntry<>(name, memberPort));
        }

        StorageServer server = new StorageServer(id, port, otherServers);
        server.run();
    }
}
