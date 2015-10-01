
package edu.nps.moves.gateway;

import java.net.*;
import java.util.*;

/**
 * Creates a multicast socket. Uses SO_REUSE_ADDR, so the socket can be
 * opened even if another program is already listening on that port.
 * @author DMcG
 */
public class DisSocketFactory 
{
    public static MulticastSocket getDisSocket(int port, String multicastGroup)
    {
        MulticastSocket socket = null;
        InetAddress group;
        
       try
       {
           // Some fancy footwork to allow us to open a UDP socket even if
           // anther program has already opened a socket on that port. Opens
           // on the wildcard address, ie all configured interfaces--both wired
           // and wireless, typically.
           
           // We want do do this because often we have another application on this
           // host generating traffic, or listening for traffic, also on the default 
           // DIS port.
           socket = new MulticastSocket(null);
           socket.setReuseAddress(true);
           socket.setBroadcast(true);
           socket.bind(new InetSocketAddress(port));
            
           if(multicastGroup != null)
           {
               group = InetAddress.getByName(multicastGroup);
               if(group.isMulticastAddress())
               {
                   socket.joinGroup(InetAddress.getByName(multicastGroup));
               }
           }
           
       }
       catch(Exception e)
       {
           System.out.println(e);
       }
       
       return socket;
    }
}
