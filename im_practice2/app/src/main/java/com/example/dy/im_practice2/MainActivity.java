package com.example.dy.im_practice2;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;


import com.example.dy.im_practice2.common.Storage;
import com.example.dy.im_practice2.common.XMPPUtil;
import com.example.dy.im_practice2.listview.ChatActivity_listview.Msg;
import com.example.dy.im_practice2.listview.MainActivity_listview.friend;
import com.example.dy.im_practice2.listview.MainActivity_listview.friendAdapter;
import com.example.dy.im_practice2.data.XMPP_data;


import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterGroup;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smackx.OfflineMessageManager;
import org.jivesoftware.smackx.packet.DelayInfo;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.example.dy.im_practice2.common.Const.KEY_USERNAME;


public class MainActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener{

    public static MainActivity _instance = null;//用于在其他活动中关闭此活动

    private List<friend> friendList = new ArrayList<friend>();
    private SwipeRefreshLayout swipeLayout;
    private friendAdapter adapter;
    Intent intent;
    private LocalBroadcastManager localBroadcastManager;
    TaxiChatManagerListener chatManagerListener = new TaxiChatManagerListener();


    private Handler handler = new Handler(){
        public void handleMessage(android.os.Message msg){
            Bundle b = msg.getData();
            String data = b.getString("data");
            String userjid = b.getString("userjid");
            if (data.equals("add friend")){
                recive_add_dialog(userjid);
            } else if(data.equals("accept add")){
                accept_add_dialog(userjid);
            } else if(data.equals("")){

            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);//取消最上面那个栏
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        _instance = this;
        initdata();
        adapter = new friendAdapter(MainActivity.this,R.layout.friend_item,friendList);
        ListView friendListView = (ListView) findViewById(R.id.friend_listview);
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        swipeLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        swipeLayout.setOnRefreshListener(MainActivity.this);
        swipeLayout.setColorSchemeResources(android.R.color.holo_blue_dark,
                    android.R.color.holo_blue_light,
                    android.R.color.holo_green_light,
                    android.R.color.holo_red_light);

        friendListView.setAdapter(adapter);
        //注册监听器
        friendListView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
           public void onItemClick(AdapterView<?> parent, View view, int position, long id){
               friend frid = friendList.get(position);
               //Toast.makeText(MainActivity.this,frid.getName(), Toast.LENGTH_LONG).show();
               intent = new Intent(MainActivity.this,ChatActivity.class);
               intent.putExtra("username",frid.getName());
               Log.d("chat","start");
               startActivity(intent);
           }
        });

        //设置消息监听
        try {
            XMPP_data.connection.getChatManager().addChatListener(chatManagerListener);
        }catch(Exception e){
            Log.d("e",e.toString());
        }
        //添加好友添加监听
        XMPP_data.connection.addPacketListener(listener, filter);

        //获取离线消息
        offline_message();

        Presence presence = new Presence(Presence.Type.available);
        XMPP_data.connection.sendPacket(presence);//设置状态为在线

    }
    //刷新好友列表
    public void onRefresh(){
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                swipeLayout.setRefreshing(false);
                //friendList.addAll(friendList);
                friendList.clear();
                adapter.notifyDataSetChanged();
                initdata();
            }
        },0);
    }
    //消息监听
    class TaxiChatManagerListener implements ChatManagerListener {

        public void chatCreated(Chat chat, boolean arg1) {

            chat.addMessageListener(new MessageListener() {
                public void processMessage(Chat arg0, Message msg) {

                    //发送消息用户
                    msg.getFrom();
                    //消息内容
                    String from = msg.getFrom();
                    String body = msg.getBody();
                    //intent.putExtra("have_msg",true);

                    //Intent intent = new Intent("new.message");
                    //localBroadcastManager.sendBroadcast(intent);
                    String[] user_name = from.split("@");
                    Log.d(user_name[0],"save in "+body);
                    new_msg_save(XMPP_data.my_username+user_name[0],body);
                }
            });
        }
    }

    /*
    * 新消息存储
    * 读取username文件，将其转化为map，将受到的消息添加到map,然后转化为json，存入文件
    * */
    private void new_msg_save(String username,String body){

        String jsonstr = read(username);
        Map map = new HashMap();
        try {
            map = toMap(jsonstr);
        }catch (Exception e){

        }
        Iterator<Map.Entry<String,String>> it = map.entrySet().iterator();
        int max_int_keynum = 0;
        while(it.hasNext()){
            Map.Entry entry = it.next();
            String key = entry.getKey().toString();
            String keynum = key.substring(1);
            int int_keynum = Integer.parseInt(keynum);
            if (max_int_keynum<int_keynum){
                max_int_keynum = int_keynum;
            }
        }

        String key = Integer.toString(max_int_keynum+1);
        map.put("y"+key,body.toString());


        save_message(username,tojsonstr(map));
    }

    //条件过滤器
    PacketFilter filter = new AndFilter(new PacketTypeFilter(Presence.class));

    /*
    packet监听器（添加请求.....）
     */
    PacketListener listener = new PacketListener() {

        @Override
        public void processPacket(Packet packet) {

            //System.out.println("PresenceService-"+packet.toXML());//xml数据
            if(packet instanceof Presence){
                Presence presence = (Presence)packet;
                String from = presence.getFrom();//发送方
                String to = presence.getTo();//接收方
                if (presence.getType().equals(Presence.Type.subscribe)) {
                    System.out.println("收到添加请求！");
                    android.os.Message msg = handler.obtainMessage();
                    Bundle b = new Bundle();
                    b.putString("data","add friend");
                    b.putString("userjid",from);
                    msg.setData(b);
                    handler.sendMessage(msg);

                } else if (presence.getType().equals(Presence.Type.subscribed)) {
                    //发送广播传递response字符串
                    String response = "恭喜，对方同意添加好友！";
                    android.os.Message msg = handler.obtainMessage();
                    Bundle b = new Bundle();
                    b.putString("data","accept add");
                    b.putString("userjid",from);
                    msg.setData(b);
                    handler.sendMessage(msg);
                } else if (presence.getType().equals(Presence.Type.unsubscribe)) {
                    //发送广播传递response字符串
                    String response = "抱歉，对方拒绝添加好友，将你从好友列表移除！";

                } else if (presence.getType().equals(Presence.Type.unsubscribed)){

                } else if (presence.getType().equals(Presence.Type.unavailable)) {

                    System.out.println(from+"好友下线！");
                    /*
                    android.os.Message msg = handler.obtainMessage();
                    Bundle b = new Bundle();
                    b.putString("data","loginsuccess");
                    handler.sendMessage(msg);
                    /*
                    if(from.equals(XMPP_data.my_password+'@'+XMPP_data.connection.getServiceName())){
                        no_connection_dialog();
                    }
                    */
                } else {
                    System.out.println(from+"好友上线！");
                }
            }
        }
    };
    /*
    * 收到好友请求dialog
    * */
    private void recive_add_dialog(final String userjid) {
        Log.d("recive_add",userjid);
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        String user = userjid;
        builder.setMessage("收到"+user+"的好友添加请求，是否接受？");
        builder.setTitle("提示");
        builder.setPositiveButton("是", new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                try {
                    Roster roster1 = XMPP_data.connection.getRoster();
                    roster1.createEntry(userjid, userjid.split("@")[0], new String[]{"test1"});//发送添加好友请求，并将该jid用户加入到自己的roster中
                    System.out.println("add friend ok");
                    onRefresh();
                    addfriend(userjid);
                }catch(Exception e){
                    System.out.print("error add ");
                }
            }
        });
        builder.setNegativeButton("否", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }


    /*
    * 接受好友添加dialog
    * */
    private void accept_add_dialog(final String userjid){
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(userjid+"接受好友请求！");
        builder.setTitle("提示");
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    Roster roster1 = XMPP_data.connection.getRoster();
                    roster1.createEntry(userjid, userjid.split("@")[0], new String[]{"test1"});//发送添加好友请求，并将该jid用户加入到自己的roster中
                    System.out.println("add friend ok");
                    onRefresh();

                }catch(Exception e){
                    System.out.print("error add ");
                    //Toast.makeText()
                }
            }
        });
    }

    /*
    * 被迫下线dialog
    * */
    private void no_connection_dialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage("您被迫下线");
        builder.setTitle("提示");
        builder.setPositiveButton("重新登录", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if(login()){
                            android.os.Message msg = handler.obtainMessage();
                            Bundle b = new Bundle();
                            b.putString("data","loginsuccess");
                            handler.sendMessage(msg);
                        }else {
                            android.os.Message msg = handler.obtainMessage();
                            Bundle b = new Bundle();
                            b.putString("data","loginsuccess");
                            msg.setData(b);
                            handler.sendMessage(msg);
                            XMPP_data.connection.disconnect();
                            finish();
                        }
                        login();
                    }
                }).start();

            }
        });
        builder.setPositiveButton("退出", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                XMPP_data.connection.disconnect();
                finish();
            }
        });
    }

    /*
    *
    * */
    //用于在线程中登陆的方法
    private boolean login(){
        try{
            android.os.Message msg = handler.obtainMessage();
            Bundle b = new Bundle();
            b.putString("data", "start");
            msg.setData(b);
            handler.sendMessage(msg); // 向Handler发送消息,更新UI
            Log.d("login","start!");
            XMPPConnection connection = XMPPUtil.getXMPPConnection(XMPP_data.my_loginserver);
            if(connection == null){
                throw new Exception("connection error!");
            }

            connection.login(XMPP_data.my_username,XMPP_data.my_password);
            //DataWarehouse.setXMPPConnection(this,connection);
            XMPP_data.connection = connection;

        }catch(Exception e){
            e.printStackTrace();
            return false;
        }
        return true;
    }

    //添加好友
    private void addfriend(String userJId){
        Presence p = new Presence(Presence.Type.subscribe);
        p.setTo(userJId);
        try{
            XMPP_data.connection.sendPacket(p);
            Toast.makeText(MainActivity.this,"向"+userJId+"发送好友请求成功！",Toast.LENGTH_LONG).show();
        }catch(Exception e){
            Log.d("add error",e.toString());
        }
    }

    private void offline_message(){
        OfflineMessageManager offlineManager = new OfflineMessageManager(XMPP_data.connection);
        try
        {
            Iterator<org.jivesoftware.smack.packet.Message> it = offlineManager.getMessages();
            System.out.println(offlineManager.supportsFlexibleRetrieval());
            System.out.println("离线消息数量: " + offlineManager.getMessageCount());
            Map<String,ArrayList<Message>> offlineMsgs = new HashMap<String,ArrayList<Message>>();

            while (it.hasNext()) {
                org.jivesoftware.smack.packet.Message message = it.next();
                System.out.println("收到离线消息, Received from 【" + message.getFrom() + "】 message: " + message.getBody());
                String fromUser = message.getFrom().split("/")[0];

                if(offlineMsgs.containsKey(fromUser))
                {
                    offlineMsgs.get(fromUser).add(message);
                }else{
                    ArrayList<Message> temp = new ArrayList<Message>();
                    temp.add(message);
                    offlineMsgs.put(fromUser, temp);
                }
            }

            offlineManager.deleteMessages(); //删除离线消息
        } catch (Exception e) {
            e.printStackTrace();
        }
        Presence presence = new Presence(Presence.Type.available);
        XMPP_data.connection.sendPacket(presence);//设置状态为在线
    }
    /*
    public void receiveOfflineMessage(){
        OfflineMessageManager offlineManager = new OfflineMessageManager(XMPP_data.connection);
        try {
            Iterator<Message> it = offlineManager.getMessages();
//			 System.out.println(offlineManager.supportsFlexibleRetrieval());
            Map<String, ArrayList<Message>> offlineMsgs = new HashMap<String, ArrayList<Message>>();
            while (it.hasNext()) {
                Message message = it.next();
                String fromJID = message.getFrom();
                DelayInfo timestamp = (DelayInfo) message.getExtension(
                        "delay", "urn:xmpp:delay");
                if (timestamp == null)
                    timestamp = (DelayInfo) message.getExtension("x",
                            "jabber:x:delay");
                long ts;
                if (timestamp != null)
                    ts = timestamp.getStamp().getTime();
                else
                    ts = System.currentTimeMillis();
                String messageBody = message.getBody();
                addChatMessageToDB(fromJID, mAccount, ChatColum.INCOMING, ts,
                        ChatColum.TEXT, messageBody,ChatColum.UN_READ, message.getPacketID());
                System.out
                        .println("收到离线消息, Received from 【" + message.getFrom()
                                + "】 message: " + message.getBody());
            }

            offlineManager.deleteMessages();

        } catch (XMPPException e) {
            e.printStackTrace();
        }
    }
*/

    /*
    * 初始化好友数据
    * */
    private void initdata(){
        Roster roster = XMPP_data.connection.getRoster();
        Collection<RosterGroup> entriesGroup = roster.getGroups();

        for(RosterGroup group: entriesGroup){
            Collection<RosterEntry> entries = group.getEntries();
            Log.d("group", group.getName());
            for (RosterEntry entry : entries) {
                if(entry.getName()!=null) {
                    Log.d("name:", entry.getName());
                    friend user = new friend(entry.getName(), R.drawable.head_logo);
                    friendList.add(user);
                    //msg_map.put(user.getName(),null);
                    Iterator<friend> it = friendList.iterator();
                    if (friendList.size() == 0) {
                        friendList.add(user);
                        while (it.hasNext()) {

                            if (!entry.getName().equals(it.next().getName())) {
                                friendList.add(user);
                                //msg_map.put(user.getName(),null);
                            }
                        }
                    }
                }
            }
        }
/*
        Collection<RosterEntry> entries = null;

        for (RosterEntry entry : entries) {
            if(entry.getName()!=null) {
                Log.d("name:", entry.getName());
                friend user = new friend(entry.getName(), R.drawable.head_logo);
                friendList.add(user);
                //msg_map.put(user.getName(),null);
                Iterator<friend> it = friendList.iterator();
                if (friendList.size() == 0) {
                    friendList.add(user);
                    while (it.hasNext()) {

                        if (!entry.getName().equals(it.next().getName())) {
                            friendList.add(user);
                            //msg_map.put(user.getName(),null);
                        }
                    }
                }
            }
        }
        */
    }
    /*
    * 给filename文件中写入data
    * */
    public void save_message(String filename,String data){
        Log.d("writefile","start");
        FileOutputStream out = null;
        BufferedWriter writer = null;
        try{

            //String str = read(filename);
            out = openFileOutput(filename,MODE_PRIVATE);
            writer = new BufferedWriter(new OutputStreamWriter(out));

            writer.write(data);
        }catch(Exception e){

        }finally {
            try{
                if (writer != null){
                    writer.close();
                }
            }catch(Exception e){
            }
        }

    }
    /*
    * 读取filename文件,并返回
    * */
    public String read(String filename){
        FileInputStream in = null;
        BufferedReader reader = null;
        StringBuilder context = new StringBuilder();
        try{
            in = openFileInput(filename);
            reader = new BufferedReader(new InputStreamReader(in));
            String line = "";
            while((line = reader.readLine())!=null){
                context.append(line);

            }
        }catch(Exception e){

        }finally{
            if(reader != null){
                try{
                    reader.close();
                }catch(Exception e){
                    try{
                        reader.close();
                    }catch(IOException e1){
                        e1.printStackTrace();
                    }
                }
            }
        }
        return context.toString();
    }
    /*
    * 将json字符串转化为map
    * */
    public Map toMap(String jsonString) throws JSONException {

        JSONObject jsonObject = new JSONObject(jsonString);

        Map result = new HashMap();
        Iterator iterator = jsonObject.keys();
        String key = null;
        String value = null;

        while (iterator.hasNext()) {

            key = (String) iterator.next();
            value = jsonObject.getString(key);
            result.put(key, value);
            //System.out.println("key:"+key+" "+"value"+value);

        }
        return result;

    }
    /*
    * 将map妆化为json字符串
    * */
    public String tojsonstr(Map map){
        //Map result = new HashMap();
        Iterator<Map.Entry<String,String>> iterator = map.entrySet().iterator();
        String key = null;
        String value = null;
        String jsonstr = null;
        while (iterator.hasNext()) {
            Map.Entry entry = iterator.next();
            key = entry.getKey().toString();
            value = map.get(key).toString();
            //result.put(key, value);
            System.out.println("key:"+key+" "+"value:"+value);
            String jsonstr1 = "\""+key+"\""+":"+"\""+value+"\"";
            if(jsonstr==null) {
                jsonstr = jsonstr1;
            }else{
                jsonstr = jsonstr + "," + jsonstr1;
            }
        }
        jsonstr = "{"+jsonstr+"}";
        System.out.println(jsonstr);
        return jsonstr;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO Auto-generated method stub
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            XMPP_data.connection.disconnect();

            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    //重写onCreateOptionMenu(Menu menu)方法，当菜单第一次被加载时调用
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //填充选项菜单（读取XML文件、解析、加载到Menu组件上）
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    //重写OptionsItemSelected(MenuItem item)来响应菜单项(MenuItem)的点击事件（根据id来区分是哪个item）
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.add_friend:
                Intent intent = new Intent(MainActivity.this,Add_friendActivity.class);
                startActivity(intent);
                break;
            case R.id.Setting:
                Intent intent1 = new Intent(MainActivity.this,SettingActivity.class);
                startActivity(intent1);
                break;
            case R.id.system_message:

                Intent system_message_intent = new Intent(MainActivity.this,system_messageActivity.class);
                //system_message_intent.putExtra("",);
                startActivity(system_message_intent);
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
