# R008 Time Measurement v2
Provenance: agent

## Measurements

**R008 (#11): Time from Publish to Visible on the Website**

Requirement: Published content must become visible on the website within one minute. Source: terminal log from 2026-10-12.

| Run | Marker | Time (s) | Method | Included in Summary | Preconditions Confirmed | Webhook | Environment | Notes |
|------|--------|----------|---------|---------|---------|---------|---------|---------|
| 1 | time-test-1 | 46 | Timer before Publish | Yes | Not Confirmed | Unknown | Local Stack | Timer started before Publish. The value can only be too high, making it an upper bound. |
| 2 | time-test-2 | 18 | Enter Method | No | Unknown |  | Local Stack | Timer started at Enter. The order between Enter and Publish varies, so the value may be too low or too high. |
| 3 | time-test-3 | 4 | Enter Method | No | Unknown |  | Local Stack | Timer started at Enter. The order between Enter and Publish varies, so the value may be too low or too high. |
| 4 | time-test-4 | 5 | Enter Method | No | Unknown |  | Local Stack | Timer started at Enter. The order between Enter and Publish varies, so the value may be too low or too high. |
| 5 | time-test-5 | 49 | Timer before Publish | Yes | Not Confirmed | Unknown | Local Stack | Timer started before Publish. The value can only be too high, making it an upper bound. Close to the 60-second limit. |
| 6 | time-test-6 | 13 | Timer before Publish | Yes | Not Confirmed | Unknown | Local Stack | Timer started before Publish. The value can only be too high, making it an upper bound. |

### Summary

| Field | Value |
|---------|---------|
| Requirement (s) | 60 |
| Minimum Required Runs | 5 |
| Included Runs | 3 |
| Shortest Upper Bound (s) | 13 |
| Longest Upper Bound (s) | 49 |
| Average Upper Bound (s) | 36 |
| Margin to Requirement (s) | 11 |
| Runs Above Requirement | 0 |
| Preconditions Confirmed | No |
| Status | Below Requirement |
| Sufficient Evidence | No |

**Comments**

- Runs using the Enter method are excluded because their values may be too low.
- The precondition applies to runs where the timer started before Publish.
- Confirm that the script started before Publish and that the marker was not already present on the page.
- Fewer than five valid runs means insufficient evidence.

---

## Discrepancy Analysis

| Run | Marker | Previously Recorded (s) | Terminal Log (s) | Difference (s) |
|------|--------|----------|----------|----------|
| 1 | time-test-1 | 195 | 46 | -149 |
| 2 | time-test-2 | 5 | 18 | 13 |
| 3 | time-test-3 | 3 | 4 | 1 |
| 4 | time-test-4 | 0 | 5 | 5 |

The terminal log is the only complete source of data, therefore the measurements are based on it. It is not known which value belongs to which publication if markers were reused.

---

## Remaining Tasks

| # | Task | Reason | Status |
|---|------|---------|---------|
| 1 | Perform at least two additional runs with the timer started before Publish, or use a script that measures against Sanity `_updatedAt` | This brings the total to five valid runs. The value 49 s is close to the 60 s limit, so an additional run could exceed it. | Open |
| 2 | Confirm for runs 1, 5, and 6 that the script started before Publish and that the marker was not already visible on the page | Only then can the values be considered reliable upper bounds. Mark "Yes" in column F. | Open |
| 3 | Execute a fallback test by disabling the webhook or pointing it to a non-responsive endpoint and measure again | Demonstrates cache expiration behavior and worst-case performance. Required separately by #59. | Open |
| 4 | Measure in an environment reachable by Sanity, such as a shared stack or test environment | Verifies that webhook delivery actually works. Sanity is unlikely to reach localhost. | Open |
| 5 | Document the results in the test record without tokens, secrets, or member data | Final acceptance criterion for #59. Include methodology and excluded values. | Open |

---

## Notes

- Source: PowerShell log provided on 2026-10-12.
- The script checks the page every second, so measurements may be up to one second higher than the actual value.
- Runs 1, 5, and 6 started timing before Publish. These values can only be too high and therefore represent upper bounds.
- Runs 2, 3, and 4 started timing at Enter and are therefore excluded.
- All runs were performed on a local stack (`localhost:8000`).
- It has not been verified that Sanity can reach localhost.
- The spread between 4 and 49 seconds may indicate cache expiration behavior rather than direct webhook invalidation.
- The requirement for five runs is based on prior guidance and not on documented requirements.
- Use only test data and never include tokens, secrets, or member data.