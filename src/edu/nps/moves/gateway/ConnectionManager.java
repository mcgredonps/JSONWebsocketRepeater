

package edu.nps.moves.gateway;

import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import redis.clients.jedis.*;
import redis.clients.util.*;

/**
 * Manages the various connections established by web pages. There may be
 * N web pages connected to the gateway. This manages the connections 
 * established by the web pages and provides a central point for sending
 * messages to all connected pages.<p>
 * 
 * We need to be careful about concurrency here--things are happening
 * async and we can be multithreaded. This is a singleton.<p>
 * 
 * This is also multi-threaded. It turns out that when a packet is received
 * from a client, it's a bad idea to repeat that packet out to all 
 * connections in the same thread. It takes a long time, and the 
 * WebPageConnection object can't receive more packets while the data
 * is being distributed out to all the other connections (which, remember,
 * takes a long time.) Instead, as soon as an incoming message is received
 * from a client, it's placed into a FIFO queue (with concurrency protection)
 * in this class. The logic in the onBinaryMessage can immediately return,
 * and the logic in this thread can retrieve the message from the queue
 * and distribute it to the other participants. Since almost everything,
 * even phones, have multi-core CPUs these days this can happen in parallel.<P>
 * 
 * The method that retrieves the next message from the queue will block 
 * if there are no messages in the queue.<p>
 * @author DMcG
 */
public class ConnectionManager implements Runnable
{
    /**
     * Thread responsible for repeating the message to all the clients
     */
    private static Thread distributionThread;
    
    /** Singleton */
    public static ConnectionManager connectionManager = null;
    
    /** Every connection established by a web page is kept in this list. This should
     * be a concurrentHashSet, but there doesn't seem to be one out of the box.
     */
    public  ConcurrentMap<WebPageConnection, WebPageConnection> connections;
    
    /** FIFO queue. The receiving connection places it into this queue, and
     * the distribution thread retrieves it, and repeats it out to all clients.
     */
    public LinkedBlockingQueue<MessageAndSender> messageQueue;
    
    
    /** Use this to get the single, shared connectionManager instance
     * @return the single, shared instance of ConnectionManager
     */
    public static synchronized ConnectionManager getConnectionManager()
    {
        if(connectionManager == null)
        {
            connectionManager = new ConnectionManager();
            distributionThread = new Thread(connectionManager);
            distributionThread.start();
        }
        
        return connectionManager;
    }
    
    /** Use the static method above to retrieve the single, shared connection manager
     */
    private ConnectionManager()
    {
         this.connections = new ConcurrentHashMap<>(); 
         this.messageQueue = new LinkedBlockingQueue<MessageAndSender>();
    }
    
    /**
     * Add a client connection to the list. Messages will be
     * relayed to this client.
     * 
     * @param aWebSocket 
     */
    public synchronized void addConnection(WebPageConnection aConnection)
    {
            connections.put(aConnection, aConnection);
    }
    
    /**
     * Remove a connection from the list of active connections. This is
     * typically called when a client closes the connection. We remove it
     * from the list of active connections.
     * 
     * @param aConnection 
     */
    public synchronized void removeConnection(WebPageConnection aConnection)
    {
          connections.remove(aConnection);
    }
    
    /**
     * Send a message we have received from the sender out to all the other
     * clients, but do NOT send it back to the client that sent it. Typically
     * the message is in string JSON format.
     * 
     * @param message message received from sender
     * @param sender the client that sent the message. The message is not sent to this client. If the
     * sender is null, the message is sent to all connected clients.
     */
    public void repeatMessage(String message, WebPageConnection sender)
    {
            try
            {
                // Optionally we can convert the string into a Java JSON
                // object and examine it for various reasons, just as 
                // area of interest management. This can be a fairly expensive
                // operation in high traffic environments.
                //JSONObject jsonMessage = new JSONObject(message);
                
                // Loop through all the connections, repeating the message out
                // (except to the connection that sent it). Note that the 
                // DISEndpoint may actually be a native network DIS connection.
                Iterator it = connections.keySet().iterator();
                while(it.hasNext())
                {
                   WebPageConnection aConnection = (WebPageConnection)it.next();

                    // Repeat the message to all clients, except the client that sent it.
                    // If the sender is null, always send it to all of the connections
                    if(sender == null || !aConnection.equals(sender))
                    {
                        aConnection.sendToClient(message);
                    }
                }
            }
            catch(Exception e)
            {
               System.out.println(e);
               e.printStackTrace();
            }
        
    }
    
    
    /**
     * A web socket has received a message,
     * and wants to distribute it to clients. It is placed into a FIFO queue,
     * which has concurrency protection, and the run() thread retrieves
     * the message and distributes it. This is better because almost all
     * CPUs are multicore these days and it runs in true parallel fashion.
     * @param data
     * @param sender 
     */
    public void enqueueMessage(String data, WebPageConnection sender)
    {
        // Dumb class to make data and reciever one object
        MessageAndSender msg = new MessageAndSender(data, sender);
        
        try
        {
            Jedis jedis = new Jedis("192.168.60.203");
            System.out.println("Created jedis ");
            jedis.set("foo", "bar");
            System.out.println("Set jedis string");
            String value = jedis.get("foo");
            System.out.println("got jedis string");
        }
        catch(Exception e)
        {
            System.out.println(e);
        }

        try
        {
            // Put into FIFO queue, in a concurrency-safe way
            messageQueue.put(msg);
        }
        catch(Exception e)
        {
            System.out.println(e);
            e.printStackTrace();
        }
        
    }
    
    public ConnectionManager.MessageAndSender getNextMessage()
    {
       MessageAndSender msg = null;
       try
            {
                // Blocks until there is a message in the queue to retrieve
                 msg = (MessageAndSender)messageQueue.take();
                return msg;
            }
            catch(Exception e)
            {
                System.out.println(e);
                e.printStackTrace();
            } 
       
       return msg;
    }
    

    /**
     * This method retrieves messages from the FIFO queue and repeats them
     * to other clients. It will block if there are no messages to get.
     */
    public void run()
    {
        // Dumb hack for concurrency issues, don't worry your pretty little head about it
        ConnectionManager mgr = ConnectionManager.getConnectionManager();
        
        while(true)
        {
            try
            {
                // Blocks until there is a message in the queue to retrieve
                MessageAndSender msg = (MessageAndSender)messageQueue.take();
                this.repeatMessage(msg.data, msg.sender);
            }
            catch(Exception e)
            {
                System.out.println(e);
                e.printStackTrace();
            }
        }
        
    }
    
    /**
     * Dumb class to hold two objects. (Map.Entry better?)
     */
    public class MessageAndSender
    {
        public String data;
        public WebPageConnection sender;
        
        public MessageAndSender(String data, WebPageConnection sender)
        {
            this.data = data;
            this.sender = sender;
        }

    }
    
    
}
