package net.dean.jraw;

import net.dean.jraw.http.AuthenticationMethod;
import net.dean.jraw.http.Credentials;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.RedditResponse;
import net.dean.jraw.http.RestRequest;
import net.dean.jraw.models.LoggedInAccount;
import org.codehaus.jackson.JsonNode;

import java.util.Date;

public class OAuth2RedditClient extends RedditClient {
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private String[] scopes;
    private Date tokenExpiration;
    private String accessToken;

    /**
     * Instantiates a new OAuth2RedditClient
     *
     * @param userAgent The User-Agent header that will be sent with all the HTTP requests.
     *                  <blockquote>Change your client's
     *                  User-Agent string to something unique and descriptive, preferably referencing your reddit
     *                  username. From the <a href="https://github.com/reddit/reddit/wiki/API">Reddit Wiki on Github</a>:
     *                  <ul>
     *                  <li>Many default User-Agents (like "Python/urllib" or "Java") are drastically limited to
     *                  encourage unique and descriptive user-agent strings.</li>
     *                  <li>If you're making an application for others to use, please include a version number in
     *                  the user agent. This allows us to block buggy versions without blocking all versions of
     *                  your app.</li>
     *                  <li>NEVER lie about your user-agent. This includes spoofing popular browsers and spoofing
     *                  other bots. We will ban liars with extreme prejudice.</li>
     *                  </ul>
     *                  </blockquote>
     */
    public OAuth2RedditClient(String userAgent) {
        super(userAgent);
        setHttpsDefault(true);
    }

    @Override
    public RestRequest.Builder request() {
        return super.request().host(HOST_OAUTH);
    }

    @Override
    public LoggedInAccount login(Credentials credentials) throws NetworkException, ApiException {
        if (credentials.getAuthenticationMethod() != AuthenticationMethod.OAUTH2_SCRIPT) {
            throw new IllegalArgumentException("Only 'script' app types supported at this moment");
        }

        RedditResponse response = getAccessResponse(credentials);

        JsonNode root = response.getJson();
        this.accessToken = root.get("access_token").asText();
        // 'scopes' is a comma separated list of OAuth scopes
        this.scopes = root.get("scope").asText().split(",");
        this.tokenExpiration = new Date();
        // Add the time the token expires
        tokenExpiration.setTime(tokenExpiration.getTime() + root.get("expires_in").asInt() * 1000);
        defaultHeaders.put(HEADER_AUTHORIZATION, "bearer " + accessToken);

        LoggedInAccount me = me();
        this.authenticatedUser = me.getFullName();
        this.authMethod = credentials.getAuthenticationMethod();

        return me;
    }

    private RedditResponse getAccessResponse(Credentials credentials) throws NetworkException {
        if (credentials.getAuthenticationMethod() != AuthenticationMethod.OAUTH2_SCRIPT) {
            throw new IllegalArgumentException("This method authenticates only 'script' apps");
        }

        return executeWithBasicAuth(request()
                        .https(true)
                        .host(HOST_SPECIAL)
                        .path("/api/v1/access_token")
                        .post(JrawUtils.args(
                                "grant_type", "password",
                                "username", credentials.getUsername(),
                                "password", credentials.getPassword()
                        ))
                        .sensitiveArgs("password")
                        .build(),
                credentials.getClientId(), credentials.getClientSecret());
    }

    @Override
    @EndpointImplementation(Endpoints.OAUTH_ME)
    public LoggedInAccount me() throws NetworkException {
        RedditResponse response = execute(request()
                .endpoint(Endpoints.OAUTH_ME)
                .build());
        // Usually we would use response.as(), but /api/v1/me does not return a "data" or "kind" node.
        return new LoggedInAccount(response.getJson());
    }

    /**
     * Gets the API scopes that the app has been registered to use ('identity', 'flair', etc.)
     * @return An array of scopes
     */
    public String[] getScopes() {
        return scopes;
    }

    /**
     * Gets the date that the authorization token will expire. You will need to request a new one after this time passes.
     * @return The date at which the authorization token will expire
     */
    public Date getTokenExpiration() {
        return tokenExpiration;
    }

    /**
     * Gets the OAuth2 access token being used to send requests
     * @return The access token
     */
    public String getAccessToken() {
        return accessToken;
    }
}
