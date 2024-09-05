package main.java.com.distributed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class ClusterSyncMember {
    private int id;
    private int port;
    private List<Integer> clusterPorts;
    private int logicalClock = 0;
    private boolean requestingCS = false;
    private Set<Integer> receivedReplies;
    private PriorityQueue<Request> requestQueue;

    private class Request {
        int timestamp;
        int senderId;
        int clientId;

        Request(int timestamp, int senderId, int clientId) {
            this.timestamp = timestamp;
            this.senderId = senderId;
            this.clientId = clientId;
        }
    }

    public ClusterSyncMember(int id, int port, List<Integer> clusterPorts) {
        this.id = id;
        this.port = port;
        this.clusterPorts = clusterPorts;
        this.receivedReplies = new HashSet<>();
        this.requestQueue = new PriorityQueue<>((a, b) -> {
            if (a.timestamp == b.timestamp) {
                return a.senderId - b.senderId;
            }
            return a.timestamp - b.timestamp;
        });
    }

    public void run() {
        System.out.println("Membro " + id + " iniciando e ouvindo na porta " + port);
        new Thread(this::listenForRequests).start();
        //new Thread(this::periodicEvaluation).start(); // Start periodic evaluation
    }

    private void listenForRequests() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Membro " + id + " está ouvindo na porta " + port);
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
            int clientId = Integer.parseInt(parts[1]);
            int timestamp = Integer.parseInt(parts[2]);

            if ("REQUEST".equals(messageType)) {
                // Trata requisições do cliente
                logicalClock = Math.max(logicalClock, timestamp) + 1;
                requestQueue.add(new Request(timestamp, id, clientId));
                propagateRequest(clientId, timestamp);
                out.println("COMMITTED");
                System.out.println("Membro " + id + " notifica o Cliente " + clientId + " que a seção crítica foi concluída.1");
            } else if ("PROPAGATE".equals(messageType)) {
                System.out.println("Entrei no PROPAGATE");
                // Trata mensagens de propagação entre membros
                int senderId = Integer.parseInt(parts[3]);
                if(senderId != id){
                    requestQueue.add(new Request(timestamp, senderId, clientId));
                }
                evaluateCriticalSection();
            } else if ("NOTIFY".equals(messageType)) {
                // Trata notificações
                notifyClient(clientId);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void propagateRequest(int clientId, int timestamp) {
        sendRequestToMember(this.port, timestamp, clientId);
        for (int port : clusterPorts) {
            //if (port != this.port) {
                sendRequestToMember(port, timestamp, clientId);
            //}
        }
    }

    private void sendRequestToMember(int memberPort, int timestamp, int clientId) {
        try (Socket socket = new Socket("localhost", memberPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("PROPAGATE:" + clientId + ":" + timestamp + ":" + id);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void evaluateCriticalSection() {
        Request topRequest = requestQueue.peek();
        System.out.println(requestQueue);
        for (Request req : requestQueue) {
            System.out.println("Timestamp: " + req.timestamp + ", SenderId: " + req.senderId + ", ClientId: " + req.clientId);
        }
        if (topRequest != null && topRequest.senderId == id) {
            // Apenas o membro com o menor timestamp e ID correto pode entrar na seção crítica
            //requestQueue.poll(); // Remove a requisição do topo
            requestCriticalSection(topRequest.clientId);
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

    public void requestCriticalSection(int clientId) {
        requestingCS = true;
        logicalClock++;

        // Simula a seção crítica
        enterCriticalSection(clientId);

        // Responde ao cliente e ao próximo membro na fila
        exitCriticalSection(clientId);
    }

    private void enterCriticalSection(int clientId) {
        System.out.println("Membro " + id + " entrando na seção crítica para o Cliente " + clientId);
        try {
            //Thread.sleep(new Random().nextInt(800) + 200); // Simula trabalho na seção crítica
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void exitCriticalSection(int clientId) {
        System.out.println("Membro " + id + " saindo da seção crítica.");
        requestQueue.poll();
        requestingCS = false;
        logicalClock++;

        // Notifica o cliente
        notifyClient(clientId);

        // Notifica o próximo membro na fila
        notifyNextMember();
    }

    private void notifyClient(int clientId) {
        // Simula notificação ao cliente
        System.out.println("Membro " + id + " notifica o Cliente " + clientId + " que a seção crítica foi concluída.");
    }

    private void notifyNextMember() {
        if (!requestQueue.isEmpty()) {
            Request nextRequest = requestQueue.peek();
            if (nextRequest != null) {
                // Notifica o próximo membro da fila
                sendNotificationToMember(nextRequest.senderId, nextRequest.clientId);
                System.out.println("Membro " + id + " notifica o Membro " + nextRequest.senderId);
            }
        }
    }

    private void sendNotificationToMember(int memberId, int clientId) {
        for (int port : clusterPorts) {
            if (port != this.port) {
                try (Socket socket = new Socket("localhost", port);
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                    out.println("NOTIFY:" + memberId + ":" + clientId);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break; // Envia a notificação para um membro. Pode ser necessário ajustar para notificar todos os membros.
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java ClusterSyncMember <id> <porta> [<portas dos outros membros>...]");
            return;
        }

        int id = Integer.parseInt(args[0]);
        int port = Integer.parseInt(args[1]);
        List<Integer> clusterPorts = Arrays.stream(args).skip(2).map(Integer::parseInt).toList();

        ClusterSyncMember member = new ClusterSyncMember(id, port, clusterPorts);
        member.run();
    }
}
