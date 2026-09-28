@import se.teaterihuskvarna.member.Welcome
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param Welcome welcome
@template.layout(copy = copy, title = copy.text("application.welcome.title"), content = @`
    <h1>${copy.text("application.welcome.heading", welcome.fullName())}</h1>
    <p>${copy.text("application.welcome.payment", copy.kronor(welcome.feeIndividualOre()),
        copy.kronor(welcome.feeHouseholdOre()), welcome.bankgiro(), welcome.fullName())}</p>
    <p>${copy.text("application.welcome.household")}</p>
    <p>${copy.text("application.welcome.login", welcome.email())}
        <a href="/logga-in">${copy.text("application.welcome.loginLink")}</a></p>
`)
