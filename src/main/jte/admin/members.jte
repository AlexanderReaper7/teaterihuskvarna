@import se.teaterihuskvarna.member.MemberDetails
@import se.teaterihuskvarna.member.MemberList
@import se.teaterihuskvarna.member.MemberService
@import java.util.List
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.RegisterFilter
@import se.teaterihuskvarna.web.Feedback
@param Copy copy
@param CsrfField csrf
@param String query
@param String fee
@param List<RegisterFilter> filters
@param MemberList list
@param Feedback feedback
@template.admin.layout(copy = copy, title = copy.text("register.heading"), content = @`
    <h1>${copy.text("register.heading")}</h1>
    @template.tag.feedback(copy = copy, feedback = feedback)
    <p class="links">
        <a href="/admin/medlemmar/ny">@template.icon.plus() ${copy.text("register.add")}</a>
        <a href="/admin/hushall">@template.icon.house() ${copy.text("household.heading")}</a>
        <a href="/admin/medlemmar.csv" download>@template.icon.download() ${copy.text("register.export")}</a>
    </p>
    <form method="get" action="/admin/medlemmar" role="search">
        <label for="q">${copy.text("register.search")}</label>
        <input id="q" name="q" type="search" value="${query}">
        @if(!fee.isEmpty())
            <input type="hidden" name="avgift" value="${fee}">
        @endif
        <button type="submit">@template.icon.search() ${copy.text("register.search.submit")}</button>
    </form>
    <nav class="choices" aria-label="${copy.text("register.filter.label")}">
        <ul>
            @for(RegisterFilter filter : filters)
                <li><a href="${filter.url()}" aria-current="${filter.current() ? "page" : null}">${filter.label()}</a></li>
            @endfor
        </ul>
    </nav>
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
                <th scope="col">${copy.text("member.household")}</th>
                <th scope="col">${copy.text("register.feeColumn")}</th>
                <th scope="col">${copy.text("register.paidDateColumn")}</th>
            </tr>
        </thead>
        <tbody>
            @for(MemberDetails member : list.members())
                <tr>
                    <td><a href="/admin/medlemmar/${member.id().value()}">${member.fullName()}</a></td>
                    <%-- A line may break before a dot or the @, and after a hyphen, so a long address wraps in its column instead of pushing the fee buttons out of the table's box. Firefox does not break after a hyphen that a digit follows unless told it may. --%>
                    !{String email = member.email();}
                    <td>@if(email != null)@for(String part : email.split("(?=[.@])|(?<=-)"))<wbr>${part}@endfor@endif</td>
                    <td class="tel">${member.phone()}</td>
                    <td>${member.household()}</td>
                    <td>
                        @template.tag.feeBadge(copy = copy, paid = member.fee().paid())
                        @if(!member.fee().paid())
                            <div class="buttons">
                                @for(String kind : member.householdId() == null ? List.of("INDIVIDUAL") : List.of("INDIVIDUAL", "HOUSEHOLD"))
                                    <form method="post" action="/admin/medlemmar/${member.id().value()}/avgift">
                                        @template.tag.csrf(csrf = csrf)
                                        <input type="hidden" name="kind" value="${kind}">
                                        <input type="hidden" name="tillbaka" value="lista">
                                        <input type="hidden" name="q" value="${query}">
                                        <input type="hidden" name="avgift" value="${fee}">
                                        <button type="submit" class="button-secondary"
                                                aria-label="${copy.text("register.markPaid.label." + kind, member.fullName())}">
                                            @template.icon.badgeCheck() ${copy.text("register.markPaid." + kind)}
                                        </button>
                                    </form>
                                @endfor
                            </div>
                        @endif
                    </td>
                    <td>@if(member.fee().paid())${copy.date(member.fee().paidAt())}@endif</td>
                </tr>
            @endfor
        </tbody>
    </table>
`)
