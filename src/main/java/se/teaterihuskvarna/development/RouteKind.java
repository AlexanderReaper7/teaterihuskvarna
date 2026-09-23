package se.teaterihuskvarna.development;

/// What answers a route.
public enum RouteKind {

    /// A JTE controller in `se.teaterihuskvarna.web`.
    PAGE("Pages"),

    /// A REST controller in `se.teaterihuskvarna.api`.
    ENDPOINT("API endpoints"),

    /// One of Spring Security's login filters, which no controller mapping shows.
    LOGIN_FILTER("Login filters");

    private final String heading;

    RouteKind(String heading) {
        this.heading = heading;
    }

    /// @return the heading the development index lists these routes under
    public String heading() {
        return heading;
    }
}
