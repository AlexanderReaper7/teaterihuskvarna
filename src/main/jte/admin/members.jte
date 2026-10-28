@import se.teaterihuskvarna.member.MemberDetails
@import se.teaterihuskvarna.member.MemberList
@import se.teaterihuskvarna.member.MemberService
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param String query
@param MemberList list
@param String error
@param String notice
@template.admin.layout(copy = copy, title = copy.text("register.heading"), content = @`
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
                <th class="nowarp" scope="col">${copy.text("member.name")}</th>
                <th class="nowarp" scope="col">${copy.text("member.email")}</th>
                <th class="nowarp"  scope="col">${copy.text("member.phone")}</th>
                <th class="nowarp" scope="col">${copy.text("member.city")}</th>
                <th class="nowarp" scope="col">${copy.text("member.household")}</th>
                <th class="nowarp" scope="col">${copy.text("register.feeColumn")}</th>
                <th class="nowarp" scope="col">${copy.text("register.paidDateColumn")}</th>
            </tr>
        </thead>
        <tbody>
            @for(MemberDetails member : list.members())
                <tr>
                    <td class="nowarp"><a href="/admin/medlemmar/${member.id()}">${member.fullName()}</a></td>
                    <td class="nowarp">${member.email()}</td>
                    <td class="nowarp">${member.phone()}</td>
                    <td class="nowarp">${member.city()}</td>
                    <td class="nowarp">${member.household()}</td>
                    <td class="nowarp">@template.tag.feeBadge(copy = copy, paid = member.fee().paid())</td>
                    <td class="nowarp">@if(member.fee().paid())${copy.date(member.fee().paidAt())}@endif</td>
                </tr>
            @endfor
        </tbody>
    </table>
`)
