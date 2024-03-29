package mazeoblig;

/**
 * <p>Title: </p>
 * RMIServer - En server som kobler seg opp � kj�rer server-objekter p�
 * rmiregistry som startes automagisk.
 * <p>Description: </p>
 *
 * <p>Copyright: Copyright (c) 2007</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
import paramaters.FunctionFlag;
import paramaters.ListFlag;
import paramaters.ParameterInterpretation;
import paramaters.ParameterInterpreter;

import java.net.*;

import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.util.Arrays;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * RMIServer starts execution at the standard entry point "public static void main";
 * It creates an instance of itself and continues processing in the constructor.
 */

public class RMIServer
{
    private final static int DEFAULT_PORT = 9000;
    private final static String DEFAULT_HOST = "undefined";
    public static int    PORT = DEFAULT_PORT;
    private static String HOST_NAME;
    private static InetAddress myAdress = null;
    private static RMIServer rmi;

    private static IGameServer gameServer;
    public static String MazeName = "Maze";
    public static String GameServerName = "GameServer";

    public RMIServer(String[] args) throws RemoteException, MalformedURLException,
            NotBoundException, AlreadyBoundException {
        getStaticInfo();

        LocateRegistry.createRegistry(PORT);
        System.out.println("RMIRegistry created on host computer " + HOST_NAME + " on port " + Integer.toString(PORT));

        IGameServer gameServer = new GameServer(4);
        Naming.rebind("//" + HOST_NAME + ":" + PORT + "/" + GameServerName, gameServer);

        System.out.println( "Bindings Finished, waiting for client requests." );
    }

    private static void getStaticInfo() {
        /**
         * Henter hostname p� min datamaskin
         */
        if (HOST_NAME == null) HOST_NAME = DEFAULT_HOST;
        if (PORT == 0) PORT = DEFAULT_PORT;
        if (HOST_NAME.equals("undefined")) {
            try {
                myAdress = InetAddress.getLocalHost();
        /*
        ** Merk at kallet under vil kunne gi meldingen :
        **
        ** "Internal errorjava.net.MalformedURLException: invalid authority"
        **
        ** i tilfeller hvor navnen p� maskinen ikke tilfredstiller
        ** spesielle krav.
        ** I s� tilfelle, bruk "localhost" i stedet.
        **
        ** Meldingen som gis har ingen betydning
        */
//        HOST_NAME = myAdress.getHostName();
                HOST_NAME = "localhost";
            }
            catch (java.net.UnknownHostException e) {
                System.err.println("Klarer ikke � finne egen nettadresse");
                e.printStackTrace(System.err);
            }
        }
        else
            System.out.println("En MazeServer kj�rer allerede, bruk den");

        System.out.println("Maze server navn: " + HOST_NAME);
        System.out.println("Maze server ip:   " + myAdress.getHostAddress());

        System.setProperty("java.rmi.server.hostname", myAdress.getHostAddress());
    }

    public static int getRMIPort() { return PORT; }
    public static String getHostName() { return HOST_NAME; }
    public static String getHostIP() { return myAdress.getHostAddress(); }

    public static void main ( String[] args ) throws Exception {
        try {
            rmi = new RMIServer(args);
        }
        catch ( java.rmi.UnknownHostException uhe ) {
            System.out.println( "Maskinnavnet, " + HOST_NAME + " er ikke korrekt." );
        }
        catch ( RemoteException re ) {
            System.out.println( "Error starting service" );
            System.out.println( "" + re );
            re.printStackTrace(System.err);
        }
        catch ( MalformedURLException mURLe )
        {
            System.out.println( "Internal error" + mURLe );
        }
        catch ( NotBoundException nbe )
        {
            System.out.println( "Not Bound" );
            System.out.println( "" + nbe );
        }
        catch ( AlreadyBoundException abe )
        {
            System.out.println( "Already Bound" );
            System.out.println( "" + abe );
        }
        System.out.println("RMIRegistry on " + HOST_NAME + ":" + PORT + "\n----------------------------");
    }  // main
}  // class RMIServer
