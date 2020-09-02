package com.tngtech.archunit.library.dependencies;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

public class GraphDrawer {
    public static void drawGraph(String text) {

        WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();
        Wurst annotatedEndpointInstance = new Wurst();
        try {
            webSocketContainer.connectToServer(annotatedEndpointInstance, new URI("ws://localhost:8080/ws/"));
        } catch (DeploymentException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        annotatedEndpointInstance.userSession.getAsyncRemote().sendText(text);
    }

    @ClientEndpoint
    public static class Wurst {
        Session userSession;
        /**
         * Callback hook for Connection open events.
         *
         * @param userSession the userSession which is opened.
         */
        @OnOpen
        public void onOpen(Session userSession) {
            System.out.println("opening websocket");
            this.userSession = userSession;
        }

        @OnMessage
        public void onMessage(String message) {

        }


        public void sendMessage(String text) {
            this.userSession.getAsyncRemote().sendText(text);
        }

    }

}
