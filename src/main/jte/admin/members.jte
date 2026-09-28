@import se.teaterihuskvarna.member.MemberDetails
@import se.teaterihuskvarna.member.MemberList
@import se.teaterihuskvarna.member.MemberService
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param String query
@param MemberList list
@param String error
@param String notice
@template.layout(copy = copy, title = copy.text("register.heading"), content = @`
    <h1>${copy.text("register.heading")}</h1>
    <p><a href="/admin">${copy.text("register.back")}</a></p>
    @if(error != null)
        <p role="alert">${error}</p>
    @endif
    @if(notice != null)
        <p role="status">${notice}</p>
    @endif
    <p>
        <a href="/admin/medlemmar/ny">${copy.text("register.add")}</a>
        · <a href="/admin/hushall">${copy.text("household.heading")}</a>
        · <a href="/admin/medlemmar.csv" download>${copy.text("register.export")}</a>
    </p>
    <form method="get" action="/admin/medlemmar" role="search">
        <label for="q">${copy.text("register.search")}</label>
        <input id="q" name="q" type="search" value="${query}">
        <button type="submit">${copy.text("register.search.submit")}</button>
    </form>
    @if(list.more())
        <p role="status">${copy.text("register.more", String.valueOf(MemberService.SEARCH_LIMIT))}</p>
    @endif
    <table>
        <caption>${copy.text("register.caption", String.valueOf(list.members().size()))}</caption>
        <thead>
            <tr>
                <th scope="col">${copy.text("member.name")}</th>
                <th scope="col">${copy.text("member.email")}</th>
                <th scope="col">${copy.text("member.phone")}</th>
                <th scope="col">${copy.text("member.city")}</th>
                <th scope="col">${copy.text("member.household")}</th>
                <th scope="col">${copy.text("register.feeColumn")}</th>
            </tr>
        </thead>
        <tbody>
            @for(MemberDetails member : list.members())
                <tr>
                    <td><a href="/admin/medlemmar/${member.id()}">${member.fullName()}</a></td>
                    <td>${member.email()}</td>
                    <td>${member.phone()}</td>
                    <td>${member.city()}</td>
                    <td>${member.household()}</td>
                    <td>@if(member.fee().paid())${copy.date(member.fee().paidAt())}@else${copy.text("fee.notPaid")}@endif</td>
                </tr>
            @endfor
        </tbody>
    </table>
`)
