# Manual WCAG 2.1 AA-Test ([#58](https://github.com/AlexanderReaper7/teaterihuskvarna/issues/58))

**Date:** 2026-10-23

Provenance: user.

**Tester:**

**Browser/device versions:** Firefox xx /Chrome xx, Android xx

**Page/workflows checked:**

**Description:** WCAG 2.1 level AA adds 12 new success criteria on top of WCAG 2.0 (e.g. 1.3.4 Orientation, 1.4.10 Reflow, 1.4.12 Text Spacing).
This document covers the manual checks required by issue ([#58](https://github.com/AlexanderReaper7/teaterihuskvarna/issues/58)) that the automated axe scans cannot perform.

**Detailed steps:**  issue-58-Wcag-mobile-test-steps.md


## 1. Keyboard only (2.1.1, 2.1.2, 2.4.3, 2.4.7)

- [ ] Every link, button and form field can be reached with tab
- [ ] No keyboard trap (can always leave menus, dialogs, widgets)
- [ ] Focus order follows the visual/logical order
- [ ] Focus indicator is always visible
- [ ] Menus open/close with Enter, space, Esc and arrow keys
- [ ] Skip links to main content work
- [ ] Focus moves sensibly when dialogs open/close

## 2. Screen reader ( 1.3.1, 3.3.1, 3.3.2, 4.1.3)

- [ ] Reading order matches the visual order
- [ ] Headings and landmarks make sense
- [ ] Every form feild has correct label
- [ ] Validation errors are announced and say what is wrong
- [ ] Dynamic feedback (success, loading, errors) is announced
- [ ] Buttons and links have meaningful names

## 3. Text alternatives (1.1.1)

- [ ] Each alt text matches the actual published image
- [ ] Decorative images use alt=""
- [ ] Images containing text repeat that text in the alt text
- [ ] Alt texts reflect current content, not placeholders

## 4. Zoom/Reflow and real phone (1.3.4, 1.4.4, 1.4.10, 1.4.12, 1.4.13)

- [ ] 200% zoom: nothing cut off or overlapping
- [ ] 400% zoom (or 320 px wide): no horizontal scrolling
- [ ] Same workflows completed on a real phone
- [ ] Works on portrait and landscape
- [ ] Touch targets are easy to hit
- [ ] Text spacing (1.4.12): no loss of content when line/paragraph/letter/word spacing is increased
- [ ] Content on hover/focus (1.4.13): tooltips can be dismissed and stay visible while hovered

## 5. Results

| Page/Workflow | Test |   Pass/Fail | Notes | Fixed failures| Dscription |
|---|---|---|---|---|---|
| Public | 1. Keyboard (focus order, visible focus, menus) |  |  |  |  |
| Public | 2. Screen reader (NVDA): order, labels, validation, dynamic feedback|  |  |  |  |  
| Public | 3. Text alternatives |  |  |  |   |
| Public | 4. Zoom/Reflow |  |  |  |  |  
| Member | 1. Keyboard (focus order, visible focus, menus) | | |  |  |
| Member | 2. Screen reader (NVDA): order, labels, validation, dynamic feedback|  |  |  |  |  
| Member | 3. Text alternatives |  |  |  |  |
| Member | 4. Zoom/Reflow | | |  |  |
| Admin | 1. Keyboard (focus order, visible focus, menus) | | |  |  |
| Admin | 2. Screen reader (NVDA): order, labels, validation, dynamic feedback|  |  |  |  |
| Admin | 3. Text alternatives |  |  |  |  |
| Admin | 4. Zoom/Reflow | | |  |  |


- [ ] All failures fixed and retested
- [ ] R006 ([#9](https://github.com/AlexanderReaper7/teaterihuskvarna/issues/9)) marked complete
