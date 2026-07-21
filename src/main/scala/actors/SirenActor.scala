package com.baldo3000.cshas
package actors

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.receptionist.{Receptionist, ServiceKey}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}

import scala.concurrent.duration.DurationDouble

object SirenActor:
  val serviceKey = ServiceKey[Command]("siren")

  private val soundInterval = 1.0.second

  enum Command extends CborSerializable:
    case Start, Stop, MakeSound

  export Command.*

  def apply(): Behavior[Command] = Behaviors.setup: ctx =>
    ctx.system.receptionist ! Receptionist.Register(serviceKey, ctx.self)
    disabled(ctx)

  private def disabled(context: ActorContext[Command]): Behavior[Command] =
    Behaviors.receiveMessagePartial:
      case Command.Start =>
        context.log.info("Starting siren")
        Behaviors.withTimers: timer =>
          timer.startSingleTimer(MakeSound, soundInterval)
          beeping(context)

  private def beeping(context: ActorContext[Command]): Behavior[Command] =
    Behaviors.receiveMessagePartial:
      case Command.Stop =>
        context.log.info("Stopping siren")
        disabled(context)
      case Command.MakeSound =>
        context.log.info("WEEEEEEEEEEEOOOOOOOO")
        Behaviors.withTimers: timer =>
          timer.startSingleTimer(MakeSound, soundInterval)
          Behaviors.same
