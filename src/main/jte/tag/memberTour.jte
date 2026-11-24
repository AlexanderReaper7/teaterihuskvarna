@import se.teaterihuskvarna.web.Copy
@param Copy copy
<%-- The members' walkthrough of Mina sidor: its steps in order, on every member page. What a member has differs,
     so a step points at a heading, and with it the whole section or page under it, where what is under it may be
     a list, a form or a line saying there is nothing. The card and the rule for when a page holds the steps are
     in tag/tour.jte. --%>
@template.tag.tour(copy = copy, root = "/medlem", steps = @`
    @template.tag.tourStep(title = copy.text("memberTour.welcome.title"), body = copy.text("memberTour.welcome.body"),
            path = "^/medlem$", href = "/medlem")
    @template.tag.tourStep(title = copy.text("memberTour.fee.title"), body = copy.text("memberTour.fee.body"),
            path = "^/medlem$", href = "/medlem", target = "#avgift")

    @template.tag.tourStep(title = copy.text("memberTour.details.title"), body = copy.text("memberTour.details.body"),
            path = "^/medlem$", href = "/medlem", target = "#kontaktuppgifter")
    @template.tag.tourStep(title = copy.text("memberTour.contact.title"), body = copy.text("memberTour.contact.body"),
            on = "form[action='/medlem/kontaktuppgifter']", href = "/medlem/kontaktuppgifter",
            target = "form[action='/medlem/kontaktuppgifter']")

    @template.tag.tourStep(title = copy.text("memberTour.household.title"), body = copy.text("memberTour.household.body"),
            path = "^/medlem$", href = "/medlem", target = "#hushall")
    @template.tag.tourStep(title = copy.text("memberTour.householdPage.title"),
            body = copy.text("memberTour.householdPage.body"),
            path = "^/medlem/hushall$", href = "/medlem/hushall", target = "main > h1")

    @template.tag.tourStep(title = copy.text("memberTour.offers.title"), body = copy.text("memberTour.offers.body"),
            path = "^/medlem/erbjudanden$", href = "/medlem/erbjudanden", target = "main > h1")
    @template.tag.tourStep(title = copy.text("memberTour.offer.title"), body = copy.text("memberTour.offer.body"),
            path = "^/medlem/erbjudanden/[0-9]+$", target = "main > h1")
    @template.tag.tourStep(title = copy.text("memberTour.documents.title"), body = copy.text("memberTour.documents.body"),
            path = "^/medlem/handlingar$", href = "/medlem/handlingar", target = "main > h1")
    @template.tag.tourStep(title = copy.text("memberTour.shifts.title"), body = copy.text("memberTour.shifts.body"),
            path = "^/medlem/volontar$", href = "/medlem/volontar", target = "main > h1")

    @template.tag.tourStep(title = copy.text("memberTour.passkeys.title"), body = copy.text("memberTour.passkeys.body"),
            path = "^/medlem$", href = "/medlem", target = "[data-passkeys] h2")
    @template.tag.tourStep(title = copy.text("memberTour.devices.title"), body = copy.text("memberTour.devices.body"),
            path = "^/medlem$", href = "/medlem", target = "[data-devices] h2")

    @template.tag.tourStep(title = copy.text("memberTour.finished.title"), body = copy.text("memberTour.finished.body"),
            path = "^/medlem$", href = "/medlem", target = "[data-tour-start]")
`)
