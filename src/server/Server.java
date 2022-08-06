package server;
// 30-31
/*
* Реализовать вывод сообшений из базы данных в TextArea
* */
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.UUID;

public class Server {
    static ArrayList<User> users = new ArrayList<>();
    static String db_url = "jdbc:mysql://127.0.0.1/chat_30_31";
    static String db_login = "root";
    static String db_pass = "";
    static Connection connection;
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
                                String clientToken = authData.get("token").toString();
                                System.out.println(login);
                                System.out.println(pass);
                                connection = DriverManager.getConnection(db_url, db_login, db_pass);
                                Statement statement = connection.createStatement();
                                ResultSet resultSet;
                                if(clientToken.equals(""))
                                    resultSet = statement.executeQuery("SELECT * FROM users WHERE login='"+login+"' AND pass='"+pass+"'");
                                else
                                    resultSet = statement.executeQuery("SELECT * FROM users WHERE token='"+clientToken+"'");
                                JSONObject jsonObject = new JSONObject();
                                if (resultSet.next()){
                                    String token = UUID.randomUUID().toString();
                                    int userId = resultSet.getInt("id");
                                    currentUser.setId(userId);
                                    currentUser.setName(resultSet.getString("name"));
                                    statement.executeUpdate("UPDATE `users` SET token='"+token+"' WHERE id="+userId);
                                    jsonObject.put("token", token);
                                    result = "success";
                                    isAuth = true;
                                }
                                jsonObject.put("authResult", result);
                                currentUser.getOut().writeUTF(jsonObject.toJSONString());
                            }
                            sendOnlineUsers();
                            sendHistoryChat(currentUser);
                            while (true){
                                String userMessage = currentUser.getIn().readUTF();
                                System.out.println(userMessage);
                                if (userMessage.equals("/getUsersName")){
                                    String result = "";
                                    for (User user : users)
                                        result += user.getName()+" ";
                                    currentUser.getOut().writeUTF(result);
                                }else{
                                    JSONObject msg = (JSONObject) jsonParser.parse(userMessage);
                                    String text = msg.get("msg").toString();
                                    int toUser = Integer.parseInt(msg.get("to_user").toString());
                                    if(toUser == 0)
                                        broadCastMessage(currentUser, userMessage);
                                    else{
                                        for (User user:users) {
                                            if(user.getId() == toUser){
                                                user.getOut().writeUTF(msg.toJSONString());
                                                break;
                                            }
                                        }
                                        continue;
                                    }
                                    Connection connection = DriverManager.getConnection(db_url, db_login, db_pass);
                                    Statement statement = connection.createStatement();
                                    statement.executeUpdate("INSERT INTO `messages`(`from_id`, `text`) VALUES ('"+currentUser.getId()+"', '"+userMessage+"')");
                                    statement.close();
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
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
        JSONArray usersList = new JSONArray();
        try {
            for (User user : users){
                JSONObject jsonUserInfo = new JSONObject();
                jsonUserInfo.put("name", user.getName());
                jsonUserInfo.put("user_id", user.getId());
                usersList.add(jsonUserInfo);
            }
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("users", usersList);
            sendJSON(jsonObject.toJSONString());
        }catch (IOException e){
            e.printStackTrace();
        }
    }
    private static void sendHistoryChat(User user) throws Exception {
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT users.id, users.name, messages.text FROM `messages`, `users` WHERE users.id = messages.from_id");
        /*
        * SELECT - выбрать
        * users.id - столбец id из таблицы users
        * users.name - столбец name из таблицы users
        * messages.text - столбец text из таблицы message
        * FROM - из таблиц(ы)
        * `messages`, `users` - перечисляем таблицы из которых выбираем данные
        * WHERE - где (это условие выбора)
        * users.id = messages.from_id - выбираем так, чтобы отправитель был равен пользователю
        * Всё это мы делаем для того, чтобы иметь не только сообщение, но и информацию об отправителе
        */
        JSONObject jsonObject = new JSONObject();
        JSONArray messages = new JSONArray(); // JSON список сообщений
        while (resultSet.next()){
            JSONObject message = new JSONObject(); // JSON объект сообщения
            message.put("name", resultSet.getString("name"));
            message.put("text", resultSet.getString("text"));
            message.put("user_id", resultSet.getInt("id"));
            messages.add(message);
        }
        jsonObject.put("messages", messages);
        user.getOut().writeUTF(jsonObject.toJSONString());
    }
}
