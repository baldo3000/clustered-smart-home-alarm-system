package com.baldo3000.cshas

import HomeAlarmProtocol.{ArmConfig, SensorConfig}
import actors.*
import actors.KeypadActor.Command.EnterPIN
import actors.SensorActor.Command.Trigger
import actors.SensorActor.SensorType

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem

import scala.util.Random

@main
def spawnListener(): Unit =
  val config = ConfigFactory.load("application.conf")
  val _ =
    ActorSystem(ClusterListener(), "ClusteredSmartHomeAlarmSystem", config)

@main
def spawnControlUnit(): Unit =
  val config = ConfigFactory.load("application.conf")
  val correctPin = sys.env.getOrElse("CORRECT_PIN", "1234").toInt
  val _ =
    ActorSystem(
      ControlUnitActor(correctPin),
      "ClusteredSmartHomeAlarmSystem",
      config
    )

@main
def spawnSensor(): Unit =
  val config = ConfigFactory.load("application.conf")
  val sensorId =
    sys.env.getOrElse("SENSOR_ID", s"sensor-${Random.nextInt(1000)}")
  val zones = sys.env.getOrElse("ZONES", "").split(",").toSet
  val sensorTypeString = sys.env.getOrElse("SENSOR_TYPE", "barrier")
  val sensorType = sensorTypeString.toLowerCase match
    case "motion"  => SensorType.Motion
    case "barrier" => SensorType.Barrier
    case _         =>
      throw new IllegalArgumentException(
        s"invalid sensor type: $sensorTypeString"
      )
  val system =
    ActorSystem(
      SensorActor(SensorConfig("sensor", zones, sensorType)),
      "ClusteredSmartHomeAlarmSystem",
      config
    )

  println("Commands: 'trigger' | 'exit'")
  Iterator
    .continually(scala.io.StdIn.readLine())
    .takeWhile(line => line != null && line != "exit")
    .foreach {
      case "trigger" => system ! Trigger
      case other     => println(s"unknown: $other")
    }

@main
def spawnKeypad(): Unit =
  val config = ConfigFactory.load("application.conf")
  val system =
    ActorSystem(KeypadActor(), "ClusteredSmartHomeAlarmSystem", config)

  println("Commands: 'enterpin <pin> <full|none|zone>' | 'exit'")
  Iterator
    .continually(scala.io.StdIn.readLine())
    .takeWhile(line => line != null && line != "exit")
    .foreach { line =>
      line.trim.split("\\s+").toList match
        case "enterpin" :: pinStr :: armStr :: Nil =>
          pinStr.toIntOption match
            case Some(pin) =>
              val arm = armStr.toLowerCase match
                case "full" => ArmConfig.Full
                case "none" => ArmConfig.None
                case zone   => ArmConfig.Partial(zone)
              system ! EnterPIN(pin, arm)
            case None =>
              println(s"invalid pin: $pinStr")

        case _ =>
          println(s"unknown command: $line")
    }

@main
def spawnSiren(): Unit =
  val config = ConfigFactory.load("application.conf")
  val _ = ActorSystem(SirenActor(), "ClusteredSmartHomeAlarmSystem", config)
