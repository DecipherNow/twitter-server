package com.deciphernow.server.admin.config

import com.twitter.app.GlobalFlag

package admin.prefix {
  object route extends GlobalFlag[String]("", "Admin Base Path")
  object static extends GlobalFlag[String]("", "Admin Base Path")
}
