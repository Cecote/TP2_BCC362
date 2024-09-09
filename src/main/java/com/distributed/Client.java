package main.java.com.distributed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class Client {
    private int id;
    private int clusterPort;
    private int clientPort;

    public Client(int id, int clusterPort, int clientPort) {
        this.id = id;
        this.clusterPort = clusterPort;
        this.clientPort = clientPort;
    }

    public void requestResource() {
        try (Socket socket = new Socket("localhost", clusterPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("Cliente " + id + " enviou uma requisição!");
            out.println("REQUEST:" + id + ":" + clientPort); // Adicionado prefixo "REQUEST"


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

                if ("COMMITTED".equals(response)) {
                    System.out.println("Cliente " + id + " recebeu COMMITTED");
                    Thread.sleep(new Random().nextInt(7000) + 1000); // Espera de 1 a 5 segundos
                    //Thread.sleep(15000);
                    requestResource(); // Repetir o pedido
                } else {
                    System.out.println("Cliente " + id + " não recebeu resposta esperada.");
                    //requestResource();
                }

            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Uso: java Client <id> <porta do cluster> <porta do cliente>");
            return;
        }

        int id = Integer.parseInt(args[0]);
        int clusterPort = Integer.parseInt(args[1]);
        int clientPort = Integer.parseInt(args[2]);

        Client client = new Client(id, clusterPort, clientPort);
        //for (int i = 0; i < 5; i++){
        // Inicia um thread para escutar as respostas
            new Thread(client::listenForResponse).start();
            client.requestResource();
        //}
    }
}
