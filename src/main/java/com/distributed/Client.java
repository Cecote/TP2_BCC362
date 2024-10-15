package main.java.com.distributed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Random;

public class Client {
    private int id;
    private Map.Entry<String, Integer> clusterCred;
    private int clientPort;
    private int cont = 0;

    public Client(int id, Map.Entry<String, Integer> clusterCred, int clientPort) {
        this.id = id;
        this.clusterCred = clusterCred;
        this.clientPort = clientPort;
    }

    public void requestResource() {
        try (Socket socket = new Socket(clusterCred.getKey(), clusterCred.getValue());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
             int requestId = new Random().nextInt(100);

            System.out.println("Cliente " + id + " enviou uma requisição! Número da requisição: " + requestId);
            out.println("REQUEST:" + id + ":" + clientPort + ":" + requestId); // Adicionado prefixo "REQUEST"


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void listenForResponse() {
        try(ServerSocket serverSocket = new ServerSocket(clientPort)) {
            while (true) {
                Socket socket = serverSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String response = in.readLine();
                String[] parts = response.split(":");
                response = parts[0];

                if ("COMMITTED".equals(response)) {
                    System.out.println("Cliente " + id + " recebeu COMMITTED para a requisição " + parts[1]);
                    //Thread.sleep(new Random().nextInt(7000) + 1000); // Espera de 1 a 5 segundos
                    Thread.sleep(3000);

                    requestResource(); // Repetir o pedido

                } else {
                    System.out.println("Cliente " + id + " não recebeu resposta esperada.");
                    //requestResource();
                }
            cont++;
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length != 4) {
            System.out.println("Uso: java Client <id> <porta do cliente> <nome do membro do cluster> <porta do cluster> ");
            return;
        }

        try {
            //Thread.sleep(new Random().nextInt(800) + 200); // Simula trabalho na seção crítica
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        int id = Integer.parseInt(args[0]);
        int clientPort = Integer.parseInt(args[1]);
        String clusterName = args[2];
        int clusterPort = Integer.parseInt(args[3]);
        Map.Entry<String, Integer> clusterCred = new AbstractMap.SimpleEntry<>(clusterName, clusterPort);

        System.out.println("Membro: " + clusterCred.getKey() + ", Porta: " + clusterCred.getValue());


        Client client = new Client(id, clusterCred, clientPort);
        // Inicia um thread para escutar as respostas
        new Thread(client::listenForResponse).start();
        Thread.sleep(1000);
        client.requestResource();
    }
}
