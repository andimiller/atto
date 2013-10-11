package atto

import scalaz._
import Scalaz._
import spire.math.UByte

// This is adapted from https://www.fpcomplete.com/school/text-manipulation/attoparsec
object Example extends App {
  import Atto._

  // IP Address
  case class IP(a: UByte, b: UByte, c: UByte, d: UByte) 

  // As a first pass we can parse an IP address in the form 128.42.30.1 by using the `ubyte` and 
  // `char` parsers directly, in a `for` comprehension.
  val ip: Parser[IP] =
    for {
      a <- ubyte
      _ <- char('.')
      b <- ubyte
      _ <- char('.')
      c <- ubyte
      _ <- char('.')
      d <- ubyte
    } yield IP(a, b, c, d)

  // Try it!
  println(ip parseOnly "foo.bar") // Fail(foo.bar,List(ubyte, int, long),Failure reading:digit)
  println(ip parseOnly "128.42.42.1") // Done(,IP(128,42,42,1))
  println(ip.parseOnly("128.42.42.1").option) // Some(IP(128,42,42,1)

  // Let's factor out the dot.
  val dot: Parser[Char] =  char('.')

  // The `<~` and `~>` combinators combine two parsers sequentially, discarding the value produced by
  // the parser on the `~` side. We can use this to simplify our comprehension a bit.
  val ip1: Parser[IP] =
    for { 
      a <- ubyte <~ dot
      b <- ubyte <~ dot
      c <- ubyte <~ dot
      d <- ubyte
    } yield IP(a, b, c, d)

  // Try it!
  println(ip1.parseOnly("128.42.42.1").option) // Some(IP(128,42,42,1)

  // We can name our parser, which provides slightly more enlightening failure messages
  val ip2 = ip1 as "ip-address"
  val ip3 = ip1 asOpaque "ip-address" // difference is illustrated below

  // Try it!
  println(ip2 parseOnly "foo.bar") // Fail(foo.bar,List(ip-address, ubyte, int, long),Failure reading:digit)
  println(ip3 parseOnly "foo.bar") // Fail(foo.bar,List(),Failure reading:ip-address)

  // Since nothing that occurs on the right-hand side of our <- appears on the left-hand side, we
  // don't actually need a monad; we can use applicative syntax here.
  val ubyteDot = ubyte <~ dot // why not?
  val ip4 = (ubyteDot |@| ubyteDot |@| ubyteDot |@| ubyte)(IP.apply) as "ip-address"

  // Try it!
  println(ip4.parseOnly("128.42.42.1").option) // Some(IP(128,42,42,1)

  // We might prefer to get some information about failure, so `either` is an, um, option
  println(ip4.parseOnly("abc.42.42.1").either) // -\/(Failure reading:digit)
  println(ip4.parseOnly("128.42.42.1").either) // \/-(IP(128,42,42,1))

  // Parsing logs

  // Here's an example log
  val logData = 
    """|2013-06-29 11:16:23 124.67.34.60 keyboard
       |2013-06-29 11:32:12 212.141.23.67 mouse
       |2013-06-29 11:33:08 212.141.23.67 monitor
       |2013-06-29 12:12:34 125.80.32.31 speakers
       |2013-06-29 12:51:50 101.40.50.62 keyboard
       |2013-06-29 13:10:45 103.29.60.13 mouse
       |""".stripMargin

  // Step 1: Define types

  // My date/time lib isn't done yet so let's just pretend
  case class Date(year: Int, month: Int, day: Int)
  case class Time(hour: Int, minutes: Int, seconds: Int)
  case class DateTime(date: Date, time: Time)

  sealed trait Product
  case object Mouse extends Product
  case object Keyboard extends Product
  case object Monitor extends Product
  case object Speakers extends Product

  case class LogEntry(entryTime: DateTime, entryIP: IP, entryProduct: Product)

  type Log = List[LogEntry]

  // Step 2: Follow the syntax

  // Parser for a fixed-width int. TODO: this should be better
  def fixed(n:Int): Parser[Int] =
    count(n, digit).flatMap { s => 
      try ok(s.mkString.toInt) catch { case nfe: NumberFormatException => err(nfe.toString) }
    }

  val date: Parser[Date] =
    for {
      y <- fixed(4) <~ char('-')
      m <- fixed(2) <~ char('-')
      d <- fixed(2)
    } yield Date(y, m, d)

  val time: Parser[Time] =
    for {
      h <- fixed(2) <~ char(':')
      m <- fixed(2) <~ char(':')
      s <- fixed(2)
    } yield Time(h, m, s)

  val dateTime: Parser[DateTime] =
    (date <~ char(' ') |@| time)(DateTime.apply)

  val product: Parser[Product] =
    string("keyboard").map(_ => Keyboard) |
    string("mouse")   .map(_ => Mouse)    |
    string("monitor") .map(_ => Monitor)  |
    string("speakers").map(_ => Speakers)

  val logEntry: Parser[LogEntry] =
    (dateTime <~ char(' ') |@| ip <~ char(' ') |@| product)(LogEntry.apply)

  val log: Parser[Log] =
    sepBy(logEntry, char('\n'))

  // Try it!
  (log parseOnly logData).option.foreach(_.foreach(println))
  // LogEntry(DateTime(Date(2013,6,29),Time(11,16,23)),IP(124,67,34,60),Keyboard)
  // LogEntry(DateTime(Date(2013,6,29),Time(11,32,12)),IP(212,141,23,67),Mouse)
  // LogEntry(DateTime(Date(2013,6,29),Time(11,33,8)),IP(212,141,23,67),Monitor)
  // LogEntry(DateTime(Date(2013,6,29),Time(12,12,34)),IP(125,80,32,31),Speakers)
  // LogEntry(DateTime(Date(2013,6,29),Time(12,51,50)),IP(101,40,50,62),Keyboard)
  // LogEntry(DateTime(Date(2013,6,29),Time(13,10,45)),IP(103,29,60,13),Mouse)

}

