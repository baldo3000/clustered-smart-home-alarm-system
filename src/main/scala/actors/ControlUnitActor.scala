package com.baldo3000.cshas
package actors

import HomeAlarmProtocol.*

import org.apache.pekko.actor.typed.receptionist.{Receptionist, ServiceKey}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import java.nio.file.{Files, Paths}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object ControlUnitActor:
  val serviceKey = ServiceKey[Command]("cu")

  sealed trait Command extends CborSerializable
  object Command:
    final case class SensorTriggered(sensorConfig: SensorConfig) extends Command
    final case class PINEntered(pin: Int, armConfig: ArmConfig) extends Command
    final case class SirensUpdated(sirens: Set[ActorRef[SirenActor.Command]])
        extends Command
    case object Arm extends Command
    case object StartAlarm extends Command

  export Command.*

  private val exitDelayDuration: FiniteDuration = 3.seconds
  private val entryDelayDuration: FiniteDuration = 5.seconds

  /** Immutable parts of the actor's running state. */
  private final case class State(
      correctPIN: Int,
      siren: Set[ActorRef[SirenActor.Command]] = Set()
  )

  def apply(correctPin: Int): Behavior[Command] =
    Behaviors.setup: ctx =>
      val listingAdapter = ctx.messageAdapter[Receptionist.Listing]:
        case SirenActor.serviceKey.Listing(sirens) =>
          SirensUpdated(sirens)

      ctx.system.receptionist ! Receptionist.Subscribe(
        SirenActor.serviceKey,
        listingAdapter
      )

      ctx.system.receptionist ! Receptionist.Register(serviceKey, ctx.self)

      val state = State(correctPin)
      val path = Paths.get("control-unit-started")
      if (Files.notExists(path)) {
        Files.createFile(path)
        disarmed(state, ctx)
      } else {
        ctx.log.info("Failure detected. Entering recovery.")
        recovery(state, ctx)
      }

  private def recovery(
      state: State,
      ctx: ActorContext[Command]
  ): Behavior[Command] =
    Behaviors.receiveMessagePartial:
      handleSirensUpdated(state, ctx)(recovery(_, ctx)).orElse:
        case PINEntered(pin, armConfig) =>
          onCorrectPIN(state, ctx, pin, "recovering the system"):
            disarmed(state, ctx)

  private def disarmed(
      state: State,
      ctx: ActorContext[Command]
  ): Behavior[Command] =
    Behaviors.receiveMessagePartial:
      handleSirensUpdated(state, ctx)(disarmed(_, ctx)).orElse:
        case PINEntered(pin, armConfig) =>
          if pin == 666 then throw new RuntimeException("Control Unit Crashed")
          onCorrectPIN(state, ctx, pin, "arming the system"):
            Behaviors.withTimers: timer =>
              timer.cancelAll()
              timer.startSingleTimer(Arm, Arm, exitDelayDuration)
              exitDelay(state, ctx, armConfig)

  private def exitDelay(
      state: State,
      ctx: ActorContext[Command],
      armConfig: ArmConfig
  ): Behavior[Command] =
    Behaviors.receiveMessagePartial:
      handleSirensUpdated(state, ctx)(exitDelay(_, ctx, armConfig)).orElse:
        case PINEntered(pin, _) =>
          onCorrectPIN(state, ctx, pin, "exiting exit delay"):
            disarmed(state, ctx)
        case Arm =>
          ctx.log.info("System armed")
          armed(state, ctx, armConfig)

  private def armed(
      state: State,
      ctx: ActorContext[Command],
      armConfig: ArmConfig
  ): Behavior[Command] =
    Behaviors.receiveMessagePartial:
      handleSirensUpdated(state, ctx)(armed(_, ctx, armConfig)).orElse:
        case PINEntered(pin, _) =>
          onCorrectPIN(state, ctx, pin, "disarming the system"):
            disarmed(state, ctx)
        case SensorTriggered(sensorConfig) =>
          if shouldTriggerAlarm(armConfig, sensorConfig) then
            ctx.log.info("Active sensor triggered, starting alarm")
            Behaviors.withTimers: timer =>
              timer.cancelAll()
              timer.startSingleTimer(
                StartAlarm,
                StartAlarm,
                entryDelayDuration
              )
              entryDelay(state, ctx)
          else
            ctx.log.info("Inactive sensor triggered, not starting alarm")
            Behaviors.same

  private def entryDelay(
      state: State,
      ctx: ActorContext[Command]
  ): Behavior[Command] =
    Behaviors.receiveMessagePartial:
      handleSirensUpdated(state, ctx)(entryDelay(_, ctx)).orElse:
        case PINEntered(pin, _) =>
          onCorrectPIN(state, ctx, pin, "exiting entry delay"):
            disarmed(state, ctx)
        case StartAlarm =>
          ctx.log.info("System alarm starting")
          state.siren.foreach(_ ! SirenActor.Start)
          alarm(state, ctx)

  private def alarm(
      state: State,
      ctx: ActorContext[Command]
  ): Behavior[Command] =
    Behaviors.receiveMessagePartial:
      handleSirensUpdated(state, ctx)(alarm(_, ctx)).orElse:
        case PINEntered(pin, _) =>
          onCorrectPIN(state, ctx, pin, "disabling alarm"):
            state.siren.foreach(_ ! SirenActor.Stop)
            disarmed(state, ctx)

  private def handleSirensUpdated(
      state: State,
      ctx: ActorContext[Command]
  )(
      nextState: State => Behavior[Command]
  ): PartialFunction[Command, Behavior[Command]] =
    case SirensUpdated(sirens) =>
      ctx.log.info(s"Sirens updated in control unit: $sirens")
      nextState(state.copy(siren = sirens))

  private inline def onCorrectPIN(
      state: State,
      ctx: ActorContext[Command],
      pin: Int,
      action: String
  )(transition: => Behavior[Command]): Behavior[Command] =
    if pin == state.correctPIN then
      ctx.log.info(s"PIN entered correctly, $action")
      transition
    else
      ctx.log.info(s"PIN entered incorrectly, not $action")
      Behaviors.same

  private def shouldTriggerAlarm(
      armConfig: ArmConfig,
      sensorConfig: SensorConfig
  ): Boolean = armConfig match
    case ArmConfig.Full          => true
    case ArmConfig.Partial(zone) => sensorConfig.zones.contains(zone)
    case _                       => false
