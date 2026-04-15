package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class OrderCreationSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl(sys.env.getOrElse("BASE_URL", "http://localhost:8080"))
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  // ── Feeders ──────────────────────────────────────────────────────────────

  val customerFeeder = Iterator.continually(Map(
    "customerId" -> (scala.util.Random.nextInt(3) + 1)
  ))

  val productFeeder = Iterator.continually(Map(
    "productId" -> (scala.util.Random.nextInt(6) + 1)
  ))

  // ── Scenarios ─────────────────────────────────────────────────────────────

  val browseProducts = scenario("Browse Products")
    .exec(
      http("List products")
        .get("/api/products")
        .check(status.is(200))
        .check(jsonPath("$[0].id").exists)
    )
    .pause(500.milliseconds)
    .feed(productFeeder)
    .exec(
      http("Get product by ID")
        .get("/api/products/#{productId}")
        .check(status.is(200))
    )

  val placeOrder = scenario("Place Order")
    .feed(customerFeeder)
    .feed(productFeeder)
    .exec(
      http("Create order")
        .post("/api/orders")
        .body(StringBody(
          """{"customerId":#{customerId},"items":[{"productId":#{productId},"quantity":1}],"shippingAddress":"123 Gatling Ave","shippingCity":"Load City","shippingState":"LC","shippingZip":"00001","shippingCountry":"USA"}"""
        ))
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("orderId"))
    )
    .pause(1.second)
    .exec(
      http("Get created order")
        .get("/api/orders/#{orderId}")
        .check(status.is(200))
    )

  // ── Simulation Setup ──────────────────────────────────────────────────────

  setUp(
    browseProducts.inject(
      rampUsers(20).during(60.seconds)
    ).andThen(
      placeOrder.inject(
        atOnceUsers(5),
        rampUsers(20).during(2.minutes),
        constantUsersPerSec(5).during(3.minutes)
      )
    )
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.percentile3.lt(500),
      global.successfulRequests.percent.gt(99)
    )
}
