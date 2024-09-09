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
    private List<Integer> clusterPorts;
    private List<Map.Entry<String, Integer>> clusterMembers = new ArrayList<>();
    private int logicalClock = 0;
    private boolean requestingCS = false;
    private Set<Integer> receivedReplies;
    private List<Request> requestQueue;
    private int clientId = 0;


    private class Request {
        int timestamp;
        int senderId;
        int clientId;

        Request(int timestamp, int senderId) {
            this.timestamp = timestamp;
            this.senderId = senderId;
            //this.clientId = clientId;
        }
    }

    public ClusterSyncMember(int id, int port, List<Map.Entry<String, Integer>> clusterMembers, Map.Entry<String, Integer> clientCred) {
        this.id = id;
        this.port = port;
        this.clusterMembers = clusterMembers;
        this.clientCred = clientCred;
        this.receivedReplies = new HashSet<>();
        System.out.println("Ordenando");
        this.requestQueue = new ArrayList<>();
        //testando();
    }

    public void run() {
        System.out.println("Membro Peer" + id + " iniciando e ouvindo na porta " + port);
        new Thread(this::listenForRequests).start();
        //new Thread(this::periodicEvaluation).start(); // Start periodic evaluation
    }

    private void listenForRequests() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Membro Peer" + id + " está ouvindo na porta " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Conexão aceita de " + clientSocket.getInetAddress());
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
            //int timestamp = Integer.parseInt(parts[2]);

            if ("REQUEST".equals(messageType)) {
                // Trata requisições do cliente
                clientId = Integer.parseInt(parts[1]);
                //this.clientPort = Integer.parseInt(parts[2]);
                int timestamp = new Random().nextInt(1000);
                logicalClock = Math.max(logicalClock, timestamp) + 1;
                requestQueue.add(new Request(timestamp, id));
                Collections.sort(requestQueue, (a, b) -> {
                    if (a.timestamp == b.timestamp) {
                        return Integer.compare(a.senderId, b.senderId);
                    }
                    return Integer.compare(a.timestamp, b.timestamp);
                });
                propagateRequest(timestamp);
                System.out.println("Membro " + id + " notifica o Cliente " + clientId + " que a seção crítica foi concluída.1");
            } else if ("PROPAGATE".equals(messageType)) {
                System.out.println("Entrei no PROPAGATE");
                // Trata mensagens de propagação entre membros
                int timestamp = Integer.parseInt(parts[1]);
                int senderId = Integer.parseInt(parts[2]);
//                System.out.println("90 " + timestamp);
//                System.out.println("91 " + senderId);
                if(senderId != id){
                    requestQueue.add(new Request(timestamp, senderId));
                    Collections.sort(requestQueue, (a, b) -> {
                        if (a.timestamp == b.timestamp) {
                            return Integer.compare(a.senderId, b.senderId);
                        }
                        return Integer.compare(a.timestamp, b.timestamp);
                    });
                }
                evaluateCriticalSection();
            } else if ("NOTIFY".equals(messageType)) {
                // Trata notificações
                notifyClient();
            } else if ("DELETE".equals(messageType)) {
                Request deleteRequest = new Request(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                Iterator<Request> iterator = requestQueue.iterator();
                while (iterator.hasNext()) {
                    Request req = iterator.next();
                    if(req.timestamp == deleteRequest.timestamp && req.senderId == deleteRequest.senderId){
                        iterator.remove();

                    }
                }
//                for (Request req : requestQueue) {
//                    if(req.timestamp == deleteRequest.timestamp && req.senderId == deleteRequest.senderId){
//                        System.out.println(req);
//                        System.out.println("Lista antes: " + requestQueue);
//                        requestQueue.remove(req);
//                        System.out.println("Lista depois: " + requestQueue);
//                    }
//                }
//                System.out.println("prepragate");
            } else if ("STATUS".equals(messageType)) {
                if(parts[1].equals("true")){
                    requestingCS = true;
                } else if(parts[1].equals("false")) {
                    requestingCS = false;
                    evaluateCriticalSection();
                }


//                Iterator<Request> iterator = requestQueue.iterator();
//                while (iterator.hasNext()) {
//                    Request req = iterator.next();
//                    if(req.timestamp == deleteRequest.timestamp && req.senderId == deleteRequest.senderId){
//                        iterator.remove();
//
//                    }
//                }
//                for (Request req : requestQueue) {
//                    if(req.timestamp == deleteRequest.timestamp && req.senderId == deleteRequest.senderId){
//                        System.out.println(req);
//                        System.out.println("Lista antes: " + requestQueue);
//                        requestQueue.remove(req);
//                        System.out.println("Lista depois: " + requestQueue);
//                    }
//                }
//                System.out.println("prepragate");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendResponseToClient() {
        try (Socket clientSocket = new Socket(clientCred.getKey(), clientCred.getValue());
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            out.println("COMMITTED");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void propagateRequest(int timestamp) {
        sendRequestToMe(this.port, timestamp, id);
        for (Map.Entry<String, Integer> tupla : clusterMembers) {
            if (tupla.getValue() != this.port) {
                sendRequestToMember(tupla, timestamp, id);
            }
        }
    }

    private void sendRequestToMe(int memberPort, int timestamp, int senderId) {
        try (Socket socket = new Socket("localhost", memberPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("PROPAGATE:" + timestamp + ":" + senderId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendRequestToMember(Map.Entry<String, Integer> tupla, int timestamp, int senderId) {
        try (Socket socket = new Socket(tupla.getKey(), tupla.getValue());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("PROPAGATE:" + timestamp + ":" + senderId);
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

            out.println("DELETE:" + topRequest.timestamp + ":" + topRequest.senderId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void propagateCriticalRegionStatus() {
        for (Map.Entry<String, Integer> tupla : clusterMembers) {
            if (tupla.getValue() != this.port) {
                sendStatusCrToMember(tupla);
            }
        }
    }

    private void sendStatusCrToMember(Map.Entry<String, Integer> tupla) {
        try (Socket socket = new Socket(tupla.getKey(), tupla.getValue());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("STATUS:" + requestingCS);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void evaluateCriticalSection() {
        System.out.println(requestQueue);
        for (Request req : requestQueue) {
            System.out.println("Timestamp: " + req.timestamp + ", SenderId: " + req.senderId);
        }
        if(!requestingCS && !requestQueue.isEmpty()){
            Request topRequest = requestQueue.get(0);
            if (topRequest != null && topRequest.senderId == id) {
                // Apenas o membro com o menor timestamp e ID correto pode entrar na seção crítica
                //requestQueue.poll(); // Remove a requisição do topo
                requestCriticalSection(topRequest);
            }
        }
    }

    private void periodicEvaluation() {
        // Avalia a seção crítica periodicamente
        while (true) {
            evaluateCriticalSection();
            try {
                Thread.sleep(1000); // Ajuste o intervalo conforme necessário
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void requestCriticalSection(Request topRequest) {
        requestingCS = true;
        logicalClock++;
        propagateCriticalRegionStatus();

        // Simula a seção crítica
        enterCriticalSection();

        // Responde ao cliente e ao próximo membro na fila
        exitCriticalSection(topRequest);
    }

    private void enterCriticalSection() {
        //System.out.println("Membro " + id + " entrando na seção crítica para o Cliente " + clientId);
        System.out.println("Membro Peer" + id + " entrando na seção crítica");
        try {
            //Thread.sleep(new Random().nextInt(800) + 200); // Simula trabalho na seção crítica
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void exitCriticalSection(Request topRequest) {
        System.out.println("Membro Peer" + id + " saindo da seção crítica.");
        sendResponseToClient();
        if (requestQueue.contains(topRequest)){
            requestQueue.remove(topRequest);
        }
        propagateExclusion(topRequest);
        requestingCS = false;
        logicalClock++;
        propagateCriticalRegionStatus();

        // Notifica o cliente
        notifyClient();

        // Notifica o próximo membro na fila
        notifyNextMember();
    }

    private void notifyClient() {
        // Simula notificação ao cliente
        System.out.println("Membro Peer" + id + " notifica o Cliente " + clientId + " que a seção crítica foi concluída.");
    }

    private void notifyNextMember() {
        if (!requestQueue.isEmpty()) {
            Request nextRequest = requestQueue.get(0);
            if (nextRequest != null) {
                // Notifica o próximo membro da fila
                sendNotificationToMember(nextRequest.senderId);
                System.out.println("Membro Peer" + id + " envia OK! para Membro Peer" + nextRequest.senderId);
            }
        }
    }



    private void sendNotificationToMember(int memberId) {
        for (Map.Entry<String, Integer> tupla : clusterMembers) {
            if (tupla.getValue() != this.port) {
                try (Socket socket = new Socket(tupla.getKey(), tupla.getValue());
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                    out.println("NOTIFY:" + memberId + ":" + clientId);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break; // Envia a notificação para um membro. Pode ser necessário ajustar para notificar todos os membros.
            }
        }
    }

    private void testando() {
        System.out.println("Cliente: " + clientCred.getKey() + ", Porta: " + clientCred.getValue());
        for(Map.Entry<String, Integer> tupla : clusterMembers) {
            System.out.println("Membro: " + tupla.getKey() + ", Porta: " + tupla.getValue());
        }
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

        // Criando uma lista para armazenar as tuplas (nome, porta)
        List<Map.Entry<String, Integer>> clusterMembers = new ArrayList<>();
        for (int i = 4; i < args.length; i += 2) {
            String name = args[i];
            int memberPort = Integer.parseInt(args[i + 1]);
            clusterMembers.add(new AbstractMap.SimpleEntry<>(name, memberPort));
        }

//        for(Map.Entry<String, Integer> tupla : clusterMembers) {
//            System.out.println("Membro: " + tupla.getKey() + ", Porta: " + tupla.getValue());
//        }

//        List<Integer> clusterPorts = Arrays.stream(args).skip(2).map(Integer::parseInt).toList();
//
        ClusterSyncMember member = new ClusterSyncMember(id, port, clusterMembers, clientCred);

        member.run();
    }
}
