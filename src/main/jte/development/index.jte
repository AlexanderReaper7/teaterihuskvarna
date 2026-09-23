@import java.util.List
@import se.teaterihuskvarna.development.LoginAccount
@import se.teaterihuskvarna.development.Route
@import se.teaterihuskvarna.development.RouteKind
@import se.teaterihuskvarna.development.RunningEnvironment
@import se.teaterihuskvarna.login.SignedIn
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@param Copy copy
@param CsrfField csrf
@param List<Route> routes
@param List<LoginAccount> accounts
@param RunningEnvironment environment
@param SignedIn signedIn
!{String schemaVersion = environment.schemaVersion();}
!{RunningEnvironment.Commit commit = environment.commit();}
!{java.time.Instant commitTime = commit == null ? null : commit.time();}
!{java.time.Instant builtAt = environment.builtAt();}
@template.layout(copy = copy, title = "Development", content = @`
    <h1>Development index</h1>
    <p>The dev profile shows this page on <code>/</code> in place of the start page.</p>

    <h2>Environment</h2>
    <dl>
        <dt>Profiles</dt>
        <dd>${String.join(", ", environment.profiles())}</dd>
        <dt>Site URL</dt>
        <dd>${environment.siteUrl()}</dd>
        <dt>Schema version</dt>
        <dd>@if(schemaVersion == null)none applied@else${"V" + schemaVersion}@endif</dd>
        <dt>Commit</dt>
        <dd>
            @if(commit == null)
                unknown, the build had no git.properties
            @else
                <code>${commit.id()}</code>@if(commitTime != null), committed ${commitTime.toString()}@endif
                @if(commit.uncommittedChanges())
                    <br><strong>Built with uncommitted changes</strong>, so the jar is not exactly this commit.
                @endif
            @endif
        </dd>
        <dt>Built</dt>
        <dd>@if(builtAt == null)unknown@else${builtAt.toString()}@endif</dd>
        <dt>Logged in</dt>
        <dd>
            @if(signedIn == null)
                nobody
            @else
                ${signedIn.fullName()}, ${signedIn.email()}, as ${signedIn.kind().name().toLowerCase(java.util.Locale.ROOT)}
            @endif
        </dd>
        <dt>Mail</dt>
        <dd><a href="${environment.mailpitUrl()}">Mailpit</a> catches every mail sent</dd>
    </dl>

    <h2>Accounts that can log in</h2>
    <p>Each button asks for a login link, as the login page would. The link arrives in <a href="${environment.mailpitUrl()}">Mailpit</a>.</p>
    <table>
        <thead>
            <tr>
                <th scope="col">Name</th>
                <th scope="col">Email</th>
                <th scope="col">Kind</th>
                <th scope="col">Link</th>
            </tr>
        </thead>
        <tbody>
            @for(LoginAccount account : accounts)
                <tr>
                    <td>${account.fullName()}</td>
                    <td>${account.email()}</td>
                    <td>${account.kind().name().toLowerCase(java.util.Locale.ROOT)}</td>
                    <td>
                        <form method="post" action="${account.loginPage()}">
                            @template.tag.csrf(csrf = csrf)
                            <input type="hidden" name="email" value="${account.email()}">
                            <button type="submit">Send link</button>
                        </form>
                    </td>
                </tr>
            @endfor
        </tbody>
    </table>

    @for(RouteKind kind : RouteKind.values())
        <h2>${kind.heading()}</h2>
        <table>
            <thead>
                <tr>
                    <th scope="col">Method</th>
                    <th scope="col">Path</th>
                    <th scope="col">Handled by</th>
                </tr>
            </thead>
            <tbody>
                @for(Route route : routes)
                    @if(route.kind() == kind)
                        <tr>
                            <td>${route.methods()}</td>
                            <td>@if(route.linkable())<a href="${route.path()}"><code>${route.path()}</code></a>@else<code>${route.path()}</code>@endif</td>
                            <td><code>${route.handledBy()}</code></td>
                        </tr>
                    @endif
                @endfor
            </tbody>
        </table>
    @endfor
`)
