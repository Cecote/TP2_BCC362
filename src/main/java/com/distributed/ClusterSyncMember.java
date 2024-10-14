package main.java.com.distributed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import static java.util.Collections.sort;

public class ClusterSyncMember {
    private int id;
    private int port;
    private int clientPort;
    private Map.Entry<String, Integer> clientCred;
    private int internStack = -1;
    private List<Boolean> ok = new ArrayList<>();
    {
        ok.add(false);
        ok.add(false);
        ok.add(false);
        ok.add(false);
        ok.add(false);
    }
    private boolean okIsAtt = false;
    private List<Map.Entry<String, Integer>> clusterMembers = new ArrayList<>();

    private List<Map.Entry<String, Integer>> storageMembers = new ArrayList<>();
    private List<Request> requestQueue = Collections.synchronizedList(new ArrayList<>());
    private int clientId = 0;
    private Request requestAct;
    private Boolean isOnCriticalSection = false;


    private class Request {
        int timestamp;
        int senderId;
        int requestId;

        Request(int timestamp, int senderId, int requestId) {
            this.timestamp = timestamp;
            this.senderId = senderId;
            this.requestId = requestId;
        }
    }

    public ClusterSyncMember(int id, int port, List<Map.Entry<String, Integer>> storageMembers, List<Map.Entry<String, Integer>> clusterMembers, Map.Entry<String, Integer> clientCred) {
        this.id = id;
        this.port = port;
        this.storageMembers = storageMembers;
        this.clusterMembers = clusterMembers;
        this.clientCred = clientCred;
        this.requestQueue = new ArrayList<>();
    }

    public void run() {
        System.out.println("Membro Peer" + id + " iniciando e ouvindo na porta " + port);
        new Thread(this::listenForRequests).start();
        //new Thread(this::periodicEvaluation).start();
    }

    private void listenForRequests() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Membro Peer" + id + " está ouvindo na porta " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                //System.out.println("Conexão aceita de " + clientSocket.getInetAddress());
                new Thread(() -> handleRequest(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleRequest(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {


            // Lê a solicitação do cliente
            String message = in.readLine();
            String[] parts = message.split(":");
            String messageType = parts[0]; // Prefixo

            if ("REQUEST".equals(messageType)) {
                okIsAtt = false;
                //System.out.println("Valor do OK no momento do request: " + ok);
                // Trata requisições do cliente
                clientId = Integer.parseInt(parts[1]);

                int requestId = Integer.parseInt(parts[3]);
                internStack = requestId;
//                System.out.println("Imprimindo dentro da função: " + internStack);
                int timestamp = new Random().nextInt(1000);
                requestAct = new Request(timestamp, id, requestId);
                requestQueue.add(requestAct);
                Collections.sort(requestQueue, (a, b) -> {
                    if (a.timestamp == b.timestamp) {
                        return Integer.compare(a.senderId, b.senderId);
                    }
                    return Integer.compare(a.timestamp, b.timestamp);
                });

                propagateRequest(timestamp, requestId);
                System.out.println("Membro " + id + " recebeu request " + requestId + " do Cliente " + clientId + "!" + " Com timestamp: " + timestamp);
            } else if ("PROPAGATE".equals(messageType)) {
                System.out.println("Op~ção propaget");
                // Trata mensagens de propagação entre membros
                int timestamp = Integer.parseInt(parts[1]);
                int senderId = Integer.parseInt(parts[2]);
                int requestId = Integer.parseInt(parts[3]);

                if(senderId == id) {
                    ok.set((id - 1), true);
                    //System.out.println("Valor do OK quando propago pra mim mesmo: " + ok);
                }
                if(senderId != id){
                    requestQueue.add(new Request(timestamp, senderId, requestId));
                    Collections.sort(requestQueue, (a, b) -> {
                        if (a.timestamp == b.timestamp) {
                            return Integer.compare(a.senderId, b.senderId);
                        }
                        return Integer.compare(a.timestamp, b.timestamp);
                    });
                }
                if(requestAct != null){
                    if(requestQueue.contains(requestAct) && id != senderId && timestamp > requestAct.timestamp){
                        okIsAtt = false;
                    }
                }
                isOkUpdated();
            } else if ("DELETE".equals(messageType)) {
                Request deleteRequest = new Request(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                Iterator<Request> iterator = requestQueue.iterator();
                while (iterator.hasNext()) {
                    Request req = iterator.next();
                    if(req.timestamp == deleteRequest.timestamp && req.senderId == deleteRequest.senderId){
                        iterator.remove();

                    }
                }
            } else if ("OK".equals(messageType)) {
                if (!isOnCriticalSection) {
                    int valorOk = Integer.parseInt(parts[1]);
                    int posicao = Integer.parseInt(parts[2]);
                    if(valorOk == 1) {
                        ok.set((posicao - 1), true);
                    } else {
                        ok.set((posicao - 1), false);
                    }

                    if(valorOk == -1) {
                        okIsAtt = false;
                    }
                    isOkUpdated();
                    //System.out.println("Valor do OK depois de atualizado na flag OK: " + ok);
                    evaluateCriticalSection();
                }

            } else if("COMMITED".equals(messageType)) {
                System.out.println("TA CHEGANDO ESSA MERDA");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void isOkUpdated() {
        System.out.println("Entrou no isOkUpdated");
        System.out.println(okIsAtt);
        System.out.println(internStack);
        if(!okIsAtt) {
            if(internStack == -1) {
                propagateOkToAll();
            } else {
                propagateOkToMinorsAndNotOkToMajors();
            }
        }
        if(okIsAtt && internStack == -1) {
            propagateOkToAll();
        }
        okIsAtt = true;
    }

    private void propagateOkToAll() {
        for (Map.Entry<String, Integer> tupla : clusterMembers) {
            if (tupla.getValue() != this.port) {
                sendOkToAll(tupla);
            }
        }
    }
    private void sendOkToAll(Map.Entry<String, Integer> tupla) {
        try (Socket socket = new Socket(tupla.getKey(), tupla.getValue());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            //System.out.println("Mensagem OK pra todos: " + tupla.getKey());
                out.println("OK:1:" + id );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void propagateOkToMinorsAndNotOkToMajors() {
        for (Map.Entry<String, Integer> tupla : clusterMembers) {
            if (tupla.getValue() != this.port) {
                sendOkToMinorsAndNotOkToMajors(tupla);
            }
        }
    }

    private void sendOkToMinorsAndNotOkToMajors(Map.Entry<String, Integer> tupla){
        List<Request> requestQueueAux = new ArrayList<>(requestQueue);
        for(Request req : requestQueueAux) {
            if(requestAct.timestamp > req.timestamp && req.senderId == (tupla.getValue() - 8080)){
                try (Socket socket = new Socket(tupla.getKey(), tupla.getValue());
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                        //System.out.println("Mensagem OK pra: " + tupla.getKey());
                        out.println("OK:1:" + id);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (requestAct.timestamp < req.timestamp && req.senderId == (tupla.getValue() - 8080)) {
                try (Socket socket = new Socket(tupla.getKey(), tupla.getValue());
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                    //System.out.println("Mensagem OK pra: " + tupla.getKey());
                    out.println("OK:-1:" + id);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
    private void sendOkToNext(Request newTopRequest) {
        for (Map.Entry<String, Integer> tupla : clusterMembers) {
            if (newTopRequest.senderId == (tupla.getValue() - 8080)) {
                try (Socket socket = new Socket(tupla.getKey(), tupla.getValue());
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                    //System.out.println("Mensagem OK pra next: " + tupla.getKey());
                    out.println("OK:1:" + id);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendResponseToClient(Request topRequest) {
        System.out.println("entrei no metodo de resposgtfa");
        System.out.println(clientCred.getKey());
        System.out.println(clientCred.getValue());
        try (Socket clientSocket = new Socket(clientCred.getKey(), clientCred.getValue());
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            out.println("COMMITTED:"+topRequest.requestId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void propagateRequest(int timestamp, int requestId) {
        System.out.println("Entrou no propagate");
        sendRequestToMe(this.port, timestamp, id, requestId);
        for (Map.Entry<String, Integer> tupla : clusterMembers) {
            if (tupla.getValue() != this.port) {
                sendRequestToMember(tupla, timestamp, id, requestId);
            }
        }
    }

    private void sendRequestToMe(int memberPort, int timestamp, int senderId, int requestId) {
        System.out.println("Entrou no propagateToMe");
        try (Socket socket = new Socket("localhost", memberPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("PROPAGATE:" + timestamp + ":" + senderId + ":" + requestId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendRequestToMember(Map.Entry<String, Integer> tupla, int timestamp, int senderId, int requestId) {
        try (Socket socket = new Socket(tupla.getKey(), tupla.getValue());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("PROPAGATE:" + timestamp + ":" + senderId + ":" + requestId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void propagateExclusion(Request topRequest) {
        for (Map.Entry<String, Integer> tupla : clusterMembers) {
            if (tupla.getValue() != this.port) {
                sendDeleteToMember(tupla, topRequest);
            }
        }
    }

    private void sendDeleteToMember(Map.Entry<String, Integer> tupla, Request topRequest) {
        try (Socket socket = new Socket(tupla.getKey(), tupla.getValue());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("DELETE:" + topRequest.timestamp + ":" + topRequest.senderId + ":" + topRequest.requestId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void evaluateCriticalSection() {
        if(!requestQueue.isEmpty()){
            Request topRequest = requestQueue.get(0);
            if (topRequest != null && topRequest.senderId == id && ok.get(0) && ok.get(1)) {
                requestCriticalSection(topRequest);
            }
        }
    }

    public void requestCriticalSection(Request topRequest) {



        if (requestQueue.contains(topRequest)){
            requestQueue.remove(topRequest);
        }
        propagateExclusion(topRequest);

        // Simula a seção crítica
        enterCriticalSection(topRequest);

        // Responde ao cliente e ao próximo membro na fila
        exitCriticalSection(topRequest);
    }

    private void enterCriticalSection(Request topRequest) {
        isOnCriticalSection = true;
        //System.out.println("Membro " + id + " entrando na seção crítica para o Cliente " + clientId);
        System.out.println("Membro Peer" + id + " entrando na seção crítica processando o request " + topRequest.requestId);

        writeToStorage(topRequest);

        try {
            //Thread.sleep(new Random().nextInt(800) + 200); // Simula trabalho na seção crítica
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void writeToStorage(Request request) {
        System.out.println("Opa!");
        Random rand = new Random();
        int randomIndex = rand.nextInt(storageMembers.size());
        Map.Entry<String, Integer> randomEntry = storageMembers.get(randomIndex);

        try (Socket storageSocket = new Socket(randomEntry.getKey(), randomEntry.getValue());
             PrintWriter out = new PrintWriter(storageSocket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(storageSocket.getInputStream()))) {

            // Envia a requisição para o storage
            out.println("WRITE:" + request.requestId + ":" + request.senderId + ":" + request.timestamp);
            System.out.println("Membro Peer" + id + " enviou a requisição " + request.requestId + " para o Cluster de Storages");

            // Aguardar a resposta do StorageServer
            String response = in.readLine(); // Aguarda até receber uma linha de resposta
            if (response != null && response.equals("COMMITED")) {
                System.out.println("Requisição " + request.requestId + " confirmada pelo StorageServer");
            } else {
                System.out.println("Erro ao confirmar a requisição " + request.requestId);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void exitCriticalSection(Request topRequest) {
        isOnCriticalSection = false;
        System.out.println("Membro Peer" + id + " saindo da seção crítica processando o request " + topRequest.requestId);
        if(requestQueue.size() != 0) {
            Request newTopRequest = requestQueue.get(0);
            sendOkToNext(newTopRequest);
        }
        isOkUpdated();
        internStack = -1;
        for(int i = 0; i < 5; i++) {
            ok.set(i, false);
        }
        okIsAtt = false;
        System.out.println("Antes de enviar ok para o cliente");
        sendResponseToClient(topRequest);


    }
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java ClusterSyncMember <id> <porta> [<portas dos outros membros>...]");
            return;
        }

        int id = Integer.parseInt(args[0]);
        int port = Integer.parseInt(args[1]);

        String clientName = args[2];
        int clientPort = Integer.parseInt(args[3]);
        Map.Entry<String, Integer> clientCred = new AbstractMap.SimpleEntry<>(clientName, clientPort);

        // Criando uma lista para armazenar as tuplas storage (nome, porta)
        List<Map.Entry<String, Integer>> storageMembers = new ArrayList<>();
        for (int i = 4; i < 10; i += 2) {
            String name = args[i];
            int memberPort = Integer.parseInt(args[i + 1]);
            storageMembers.add(new AbstractMap.SimpleEntry<>(name, memberPort));
        }

        // Criando uma lista para armazenar as tuplas cluster members (nome, porta)
        List<Map.Entry<String, Integer>> clusterMembers = new ArrayList<>();
        for (int i = 10; i < args.length; i += 2) {
            String name = args[i];
            int memberPort = Integer.parseInt(args[i + 1]);
            clusterMembers.add(new AbstractMap.SimpleEntry<>(name, memberPort));
        }
        System.out.println("ClusterStorage: " + storageMembers);
        System.out.println("MembersSync: " + clusterMembers);

        ClusterSyncMember member = new ClusterSyncMember(id, port, storageMembers, clusterMembers, clientCred);

        member.run();
    }
}
