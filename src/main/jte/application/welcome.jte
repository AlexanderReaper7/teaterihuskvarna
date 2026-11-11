@import se.teaterihuskvarna.member.Welcome
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.LoginPage
@param Copy copy
@param Welcome welcome
<%-- The link logs in at once in the browser that applied, and leads to the address form in any other,
     through the page a mailed link opens: docs/decisions/0015, "Logging in from the welcome page". --%>
!{String login = welcome.loginToken() == null
        ? LoginPage.MEMBER.path()
        : LoginPage.MEMBER.linkPath() + "?token=" + welcome.loginToken();}
@template.layout(copy = copy, title = copy.text("application.welcome.title"), content = @`
    <h1>${copy.text("application.welcome.heading", welcome.fullName())}</h1>
    <h2>${copy.text("fee.heading", String.valueOf(welcome.fee().year()))}
        @template.tag.feeBadge(copy = copy, paid = welcome.fee().paid())</h2>
    <p>${copy.text("application.welcome.payment", copy.kronor(welcome.feeIndividualOre()),
        copy.kronor(welcome.feeHouseholdOre()), welcome.bankgiro(), welcome.email())}</p>
    <p>${copy.text("application.welcome.household")}</p>
    <p>${copy.text("application.welcome.login", welcome.email())}
        <a href="${login}">${copy.text("application.welcome.loginLink")}</a></p>
`)
