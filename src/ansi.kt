package org.develar.j2ktCommiter

import org.fusesource.jansi.Ansi

fun Ansi.bold(text: String) = bold().a(text).boldOff()

fun Ansi.green(text: String) = fg(Ansi.Color.GREEN).a(text).reset()