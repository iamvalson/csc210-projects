package game.core;

import java.util.UUID;

public class Player{
    private final String id;
    private final String name;
    private volatile GameSession session;

    public Player(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = (name == null || name.isEmpty()) ? "anonymous" : name;
    }

    public String getId() {return id;}
    public String getName() {return name;}
    public GameSession getSession() {return session;}
    public void setSession(GameSession session) {this.session = session;}
}