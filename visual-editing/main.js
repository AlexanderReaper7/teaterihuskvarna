// Connects a preview page to the Studio's Presentation tool, which waits for
// this connection: docs/decisions/0021-content-from-sanity.md. Presentation
// then lists the documents on the page, and a click on an element with a
// data-sanity attribute, which the templates write in a preview, opens that
// field in the editor. src/main/jte/layout.jte loads this file on preview pages only.
import {enableVisualEditing} from '@sanity/visual-editing'

// Studio saves a draft shortly after the last keystroke, and a change reaches
// the page only through a reload. Waiting for the edits to pause reloads once
// per pause instead of once per saved keystroke.
const SETTLE_MS = 1000

let reload

enableVisualEditing({
  // Every page is a full load, so the page reports its own address when it
  // loads, and an address typed in Presentation becomes a new load.
  history: {
    subscribe(navigate) {
      navigate({type: 'push', url: here()})
      return () => {}
    },
    update(update) {
      if (update.type === 'pop') {
        window.history.back()
        return
      }
      // Another frame in the Studio can post to this one too. Following
      // only an address on this site keeps a javascript: address out. The
      // whole address is followed, not its path: a path such as
      // //other.example/ names another host.
      const target = new URL(update.url, window.location.href)
      if (target.origin !== window.location.origin || target.href === window.location.href) {
        return
      }
      if (update.type === 'replace') {
        window.location.replace(target.href)
      } else {
        window.location.assign(target.href)
      }
    },
  },
  // The refresh button keeps Sanity's default, a reload. A saved draft
  // reloads too: drafts are never cached, see ContentCache.
  refresh(payload) {
    if (payload.source === 'mutation') {
      clearTimeout(reload)
      reload = setTimeout(() => window.location.reload(), SETTLE_MS)
    }
    return false
  },
})

function here() {
  return window.location.pathname + window.location.search + window.location.hash
}
