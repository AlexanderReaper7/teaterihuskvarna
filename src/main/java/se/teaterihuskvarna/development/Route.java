package se.teaterihuskvarna.development;

/// One path the application answers.
///
/// @param kind      what answers it
/// @param methods   the HTTP methods, such as `GET` or `GET, POST`
/// @param path      the path pattern, such as `/admin/administratorer/{id}/ta-bort`
/// @param handledBy the controller method, or the filter, that answers
public record Route(RouteKind kind, String methods, String path, String handledBy) {

    /// @return whether a plain link can open it: a page answering GET with no
    ///         variable in its path
    public boolean linkable() {
        return kind == RouteKind.PAGE && methods.contains("GET") && !path.contains("{");
    }
}
