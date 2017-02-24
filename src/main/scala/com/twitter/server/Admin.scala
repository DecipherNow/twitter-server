package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.stats.{AggregateWithHistogramDetails, DelegatingStatsReceiver, WithHistogramDetails}
import com.twitter.server.handler._
import com.twitter.server.view._


object Admin {

  /**
   * Common constants for [[AdminHttpServer.Route]]'s `group`.
   */
  object Grouping {
    val ProcessInfo = "Process Info"
    val PerfProfile = "Performance Profile"
    val Utilities = "Utilities"
    val Metrics = "Metrics"
  }

  /**
   * Constants for Admin endpoints.
   */
  object Path {
    val Root = ""
    val Admin = "/admin"
    val Clients = Admin + "/clients/"
    val Servers = Admin + "/servers/"
    val Files = Admin + "/files/"
  }
}

/**
 * Defines many of the default `/admin/` HTTP routes.
 */
trait Admin { self: App with AdminHttpServer with Stats =>
  import Admin._
  import AdminHttpServer.Route
  import Admin.Grouping

  def simplePath : String // todo: remove from gmf.

  override protected def routes: Seq[Route] = {
    val standardRoutes = Seq(
      Route(
        path = PathResolver.createRoutePath(Path.Root), handler = new AdminRedirectHandler,
        alias = "Admin Redirect", group = None, includeInIndex = false),
      Route(
        path = PathResolver.createRoutePath(Path.Admin), handler = new SummaryHandler,
        alias = "Summary", group = None, includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/server_info"), handler = new TextBlockView andThen new ServerInfoHandler(self),
        alias = "Build Properties", group = Some(Grouping.ProcessInfo), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/contention"), handler = new TextBlockView andThen new ContentionHandler,
        alias = "Contention", group = Some(Grouping.PerfProfile), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/lint"), handler = new LintHandler(),
        alias = "Lint", group = Some(Grouping.ProcessInfo), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/lint.json"), handler = new LintHandler(),
        alias = "Lint", group = Some(Grouping.ProcessInfo), includeInIndex = false),
      Route(
        path = PathResolver.createRoutePath("/admin/failedlint"), handler = new FailedLintRuleHandler,
        alias = "Failed Lint Rules", group = None, includeInIndex = false),
      Route(
        path = PathResolver.createRoutePath("/admin/threads"), handler = new ThreadsHandler,
        alias = "Threads", group = Some(Grouping.ProcessInfo), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/threads.json"), handler = new ThreadsHandler,
        alias = "Threads", group = Some(Grouping.ProcessInfo), includeInIndex = false),
      Route(
        path = PathResolver.createRoutePath("/admin/announcer"), handler = new TextBlockView andThen new AnnouncerHandler,
        alias = "Announcer", group = Some(Grouping.ProcessInfo), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/dtab"), handler = new TextBlockView andThen new DtabHandler,
        alias = "Dtab", group = Some(Grouping.ProcessInfo), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/pprof/heap"), handler = new HeapResourceHandler,
        alias = "Heap", group = Some(Grouping.PerfProfile), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/pprof/profile"), handler = new ProfileResourceHandler(Thread.State.RUNNABLE),
        alias = "Profile", group = Some(Grouping.PerfProfile), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/pprof/contention"), handler = new ProfileResourceHandler(Thread.State.BLOCKED),
        alias = "Blocked Profile", group = Some(Grouping.PerfProfile), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/ping"), handler = new ReplyHandler("pong"),
        alias = "Ping", group = Some(Grouping.Utilities), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/shutdown"), handler = new ShutdownHandler(this),
        alias = "Shutdown", group = Some(Grouping.Utilities), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/tracing"), handler = new TracingHandler,
        alias = "Tracing", group = Some(Grouping.Utilities), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/events"), handler = new EventsHandler,
        alias = "Events", group = Some(Grouping.Utilities), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/events/record/"), handler = new EventRecordingHandler(),
        alias = "EventRecording", group = None, includeInIndex = false),
      Route(
        path = PathResolver.createRoutePath("/admin/logging"), handler = new LoggingHandler,
        alias = "Logging", group = Some(Grouping.Utilities), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/metrics"), handler = new MetricQueryHandler,
        alias = "Watch", group = Some(Grouping.Metrics), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath(Path.Clients), handler = new ClientRegistryHandler(Path.Clients),
        alias = "Clients", group = None, includeInIndex = false),
      Route(
        path = PathResolver.createRoutePath(Path.Servers), handler = new ServerRegistryHandler(Path.Servers),
        alias = "Servers", group = None, includeInIndex = false),
      Route(
        path = PathResolver.createRoutePath(Path.Files),
        handler = ResourceHandler.fromJar(
          baseRequestPath = Path.Files,
          baseResourcePath = "twitter-server"),
        alias = "Files", group = None, includeInIndex = false),
      Route(
        path = PathResolver.createRoutePath("/admin/registry.json"), handler = new RegistryHandler,
        alias = "Registry", group = Some(Grouping.ProcessInfo), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/toggles"), handler = new ToggleHandler(),
        alias = "Toggles", group = Some(Grouping.ProcessInfo), includeInIndex = true),
      Route(
        path = PathResolver.createRoutePath("/admin/toggles/"), handler = new ToggleHandler(),
        alias = "Toggles", group = None, includeInIndex = false),
      Route(
        path = "/favicon.ico", ResourceHandler.fromJar(
          baseRequestPath = "/",
          baseResourcePath = "twitter-server/img"),
        alias = "Favicon", group = None, includeInIndex = false)
    )

    // If histograms are available, add an additional endpoint
    val histos = DelegatingStatsReceiver.all(statsReceiver)
      .collect { case histo: WithHistogramDetails => histo }
    standardRoutes ++ {
      if (histos.nonEmpty) {
        val aggregate = AggregateWithHistogramDetails(histos)

        val histogramHandler = new HistogramQueryHandler(aggregate)
        Seq(
          Route(
            path = PathResolver.createRoutePath("/admin/histograms"), handler = histogramHandler,
            alias = "Histograms", group = Some(Grouping.Metrics), includeInIndex = true),
          Route(
            path = PathResolver.createRoutePath("/admin/histograms.json"),
            handler = histogramHandler,
            alias = "/admin/histograms.json",
            group = Some(Grouping.Metrics),
            includeInIndex = false)
        )
      } else Nil
    }
  }
}
