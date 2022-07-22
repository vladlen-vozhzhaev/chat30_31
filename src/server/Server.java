package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Locale;

/*
*  После подключения клиента сервер запрашивает имя
*  В дальнейшем все сообщения будут отправляться в формате Имя: сообщение
* */

public class Server {
    static ArrayList<User> users = new ArrayList<>();
    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(9743);
            System.out.println("Сервер запущен");
            while (true){
                Socket socket = serverSocket.accept();
                User currentUser = new User(socket);
                users.add(currentUser);
                System.out.println("Клиент подключился");
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            currentUser.getOut().writeUTF("Введите имя");
                            String name = currentUser.getIn().readUTF();
                            currentUser.setName(name);
                            while (true){
                                String userMessage = currentUser.getIn().readUTF();
                                System.out.println(userMessage);
                                if (userMessage.equals("/getUsersName")){
                                    String result = "";
                                    for (User user : users)
                                        result += user.getName()+" ";
                                    currentUser.getOut().writeUTF(result);
                                }else
                                    broadCastMessage(currentUser, userMessage);
                            }
                        } catch (IOException e) {
                            System.out.println("Клиент отключился");
                            users.remove(currentUser);
                            broadCastMessage(currentUser, "покинул чат");
                        }
                    }
                });
                thread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void broadCastMessage(User currentUser, String message){
        for(User user : users){
            if(user.getUuid().toString().equals(currentUser.getUuid().toString())) continue;
            try {
                user.getOut().writeUTF(currentUser.getName()+": "+message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
