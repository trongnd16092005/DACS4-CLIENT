package com.example.scribble.rmi;



import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import com.example.scribble.common.api.IBookService;


public class RMIClient {
    private static RMIClient instance;
    private IBookService bookService;


    private RMIClient() {}


    public static synchronized RMIClient getInstance() {
        if (instance == null) instance = new RMIClient();
        return instance;
    }


    public void connect(String host, int port) throws Exception {
        Registry registry = LocateRegistry.getRegistry(host, port);
        bookService = (IBookService) registry.lookup("BookService");
    }


    public IBookService getBookService() { return bookService; }
}