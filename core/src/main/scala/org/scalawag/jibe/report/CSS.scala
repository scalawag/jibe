package org.scalawag.jibe.report

import scalacss.DevDefaults._

object CSS extends StyleSheet.Standalone {
  import dsl._

  private[this] object colors {
    val statusPending  = c"#DDDDDD"
    val statusRunning  = c"#C3E6fC"
    val statusSuccess  = c"#DFF2BF"
    val statusUnneeded = c"#FEEFB3"
    val statusNeeded   = statusSuccess
    val statusFailure  = c"#FFBABA"
    val statusBlocked  = statusFailure

    val runListBorder = grey(160)
    val runListSelectionColor = black
    val runListBackgroundColor = white
  }

  "div#run-list" - (
    position.fixed,
    top(0 px),
    width(10 em),
    height(100 %%),
    border(1 px, solid, colors.runListBorder),
    backgroundColor(colors.runListBackgroundColor),
    overflowY.auto,
    zIndex(100000),

    &("table") - (
      width(100.%%),

      &("tr") - (
        border.none
      ),

      &("tr.entry") - (
        cursor.pointer,
        height(2 em),
        borderBottom(1 px, solid, colors.runListBorder)
        ,
        // This starts as invisible and is eased in by jQuery when it's added to the DOM.
        display.none
      ),

      &("tr#latest") - (
        display.tableRow
      ),

      &("tr.entry.selected") - (
        cursor.auto,

        &("td.selection") - (
          backgroundColor(colors.runListSelectionColor)
        ),

        &("td.hover") - (
          backgroundColor(colors.runListSelectionColor)
        )
      ),

      &("tr.entry").hover - (
        &("td.hover") - (
          backgroundColor(colors.runListSelectionColor)
        )
      ),

      &("td") - (
      padding(0 em)
      ),

      &("td.selection") - (
        width(0.4 em)
      ),

      &("td.hover") - (
        width(0.1 em)
      ),

      &("td.icon") - (
        width(2 em),
        verticalAlign.middle,
        textAlign.center
      ),

      &("td.text") - (
        verticalAlign.middle,
        textAlign.left,
        paddingBottom(0.25 em),
        paddingTop(0.25 em),

        &("div.relative") - (
          fontSize(80 %%),
          fontWeight.bold
        ),

        &("div.timestamp") - (
          fontSize(60 %%),
          marginTop(0.25 em)
        )
      ),

      &("tr.entry.success")  - backgroundColor(colors.statusSuccess),
      &("tr.entry.needed")   - backgroundColor(colors.statusNeeded),
      &("tr.entry.failure")  - backgroundColor(colors.statusFailure),
      &("tr.entry.blocked")  - backgroundColor(colors.statusBlocked),
      &("tr.entry.running")  - backgroundColor(colors.statusRunning),
      &("tr.entry.unneeded") - backgroundColor(colors.statusUnneeded),
      &("tr.entry.pending")  - backgroundColor(colors.statusPending),

/* uncomment for solid black selection indicator
tr.entry.selected {
  background-color: black;
}

tr.entry.selected {
  color: white;
}

tr.entry.selected.success,
tr.entry.selected.needed
{
  color: #DFF2BF;
}

tr.entry.selected.failure {
  color: #FFBABA;
}

tr.entry.selected.blocked {
  color: #FFBABA;
}

tr.entry.selected.running {
  color: #c3e6fc;
}

tr.entry.selected.unneeded {
  color: #FEEFB3;
}

tr.entry.selected.pending {
  color: #DDDDDD;
}
*/

      // Do this here directly instead of using the fa-spin class because this is entirely CSS-based (no class
      // manipulation needed when the parent entry's class changes).

      &("tr.entry.selected i.fa-refresh") - (
        animation := "fa-spin 2s infinite linear"
      )
    )
  )

  "div#run-header" - (

  )

  val rendered = this.render.toString
}
