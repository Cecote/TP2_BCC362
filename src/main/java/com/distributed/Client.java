package main.java.com.distributed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;

public class Client {
    private int id;
    private int clusterPort;

    public Client(int id, int clusterPort) {
        this.id = id;
        this.clusterPort = clusterPort;
    }

    public void requestResource() {
        try (Socket socket = new Socket("localhost", clusterPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {


            out.println("REQUEST:" + id); // Adicionado prefixo "REQUEST"

            String response = in.readLine();
            if ("COMMITTED".equals(response)) {
                System.out.println("Cliente " + id + " recebeu COMMITTED");
                Thread.sleep(new Random().nextInt(7000) + 1000); // Espera de 1 a 5 segundos
                //Thread.sleep(15000);
                //requestResource(); // Repetir o pedido
            } else {
                System.out.println("Cliente " + id + " não recebeu resposta esperada.");
                //requestResource();
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Uso: java Client <id> <porta do cluster>");
            return;
        }

        int id = Integer.parseInt(args[0]);
        int clusterPort = Integer.parseInt(args[1]);

        Client client = new Client(id, clusterPort);
        client.requestResource();
    }
}
