package server;
// 30-31
/*
* Если пользователь приложения уже авторизовывался, то при запуске приложения, не требовать авторизацию
*
* */
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.w3c.dom.ls.LSOutput;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;

/*
*  После подключения клиента сервер запрашивает имя
*  В дальнейшем все сообщения будут отправляться в формате Имя: сообщение
* */

public class Server {
    static ArrayList<User> users = new ArrayList<>();
    static String db_url = "jdbc:mysql://127.0.0.1/chat_30_31";
    static String db_login = "root";
    static String db_pass = "";
    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(9743);
            System.out.println("Сервер запущен");
            Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance();
            while (true){
                Socket socket = serverSocket.accept();
                User currentUser = new User(socket);
                users.add(currentUser);
                System.out.println("Клиент подключился");
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            boolean isAuth = false;
                            JSONParser jsonParser = new JSONParser();
                            while (!isAuth){
                                String result = "error";
                                JSONObject authData = (JSONObject) jsonParser.parse(currentUser.getIn().readUTF());
                                String login = authData.get("login").toString();
                                String pass = authData.get("pass").toString();
                                Connection connection = DriverManager.getConnection(db_url, db_login, db_pass);
                                Statement statement = connection.createStatement();
                                ResultSet resultSet = statement.executeQuery("SELECT * FROM users WHERE login='"+login+"' AND pass='"+pass+"'");
                                if (resultSet.next()){
                                    currentUser.setName(resultSet.getString("name"));
                                    result = "success";
                                    isAuth = true;
                                }
                                JSONObject jsonObject = new JSONObject();
                                jsonObject.put("authResult", result);
                                currentUser.getOut().writeUTF(jsonObject.toJSONString());
                            }
                            sendOnlineUsers();
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
                        } catch (Exception e) {
                            System.out.println("Клиент отключился");
                            users.remove(currentUser);
                            sendOnlineUsers();
                            broadCastMessage(currentUser, "покинул чат");
                        }
                    }
                });
                thread.start();
            }
        } catch (Exception e) {
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

    private static void sendJSON(String jsonString) throws IOException {
        for (User user : users){
            user.getOut().writeUTF(jsonString);
        }
    }

    private static void sendOnlineUsers() {
        ArrayList<String> usersName = new ArrayList<>();
        try {
            for (User user : users){
                usersName.add(user.getName());
            }
            String resultJSON = JSONArray.toJSONString(usersName);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("users", resultJSON);
            sendJSON(jsonObject.toJSONString());
        }catch (IOException e){
            e.printStackTrace();
        }
    }
}
