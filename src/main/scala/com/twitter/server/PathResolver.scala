package com.twitter.server

import com.twitter.server.{config => configuration}

/**
  * Created by ghershfield on 2/24/17.
  */
object PathResolver {


  val routePrefix  = configuration.admin.prefix.route()
  val staticPrefix = configuration.admin.prefix.static()

  def createRoutePath(partialRoute: String) : String = {
    println("PathResolver.createRoutePath ... building the path for U => ")
    routePrefix + partialRoute
  }

  def createStaticPath(partialPath: String) : String = {
    println("PathResolver.createStaticPath ... building the path for U => ")
    staticPrefix + partialPath
  }

}
