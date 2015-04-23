package jenkins.plugins.http_request;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by rmiville on 4/23/15.
 */
public class Artifact {
    String name;
    String state;
    String team;
    String application;
    Map<String, String> tags;

    public Artifact(String name, String state, String team, String application, Map<String, String> tags) {
        this.name = name;
        this.state = state;
        this.team = team;
        this.application = application;
        this.tags = tags;
    }

    public Artifact(String name, String state, String team, String application) {
        this(name, state, team, application, new HashMap<String, String>());
    }
}
