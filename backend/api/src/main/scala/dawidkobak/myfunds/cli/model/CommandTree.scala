package dawidkobak.myfunds.cli.model

import dawidkobak.myfunds.cli.CliApplication.Environment
import zio.{Chunk, RIO}
import zio.cli.{Args, Command, Options}

sealed trait CommandNode
final case class CommandLeaf[T <: CliAction](definition: CommandDefinition[T]) extends CommandNode
final case class CommandBranch(name: String, children: List[CommandNode]) extends CommandNode

object CommandTree {
  def buildParser(node: CommandNode): Command[CliAction] = node match {
    case CommandLeaf(definition) => definition.syntax
    case CommandBranch(name, children) =>
      val parsers = children.map(buildParser)
      if (parsers.isEmpty) throw new IllegalArgumentException("Error during creating commands")

      val parent = Command(name, Options.none, Args.none)
      parsers.tail match {
        case Nil => parent.subcommands(parsers.head)
        case next :: remaining => parent.subcommands(parsers.head, next, remaining*)
      }
  }

  def executionHandlers(arguments: Chunk[String], node: CommandNode): List[PartialFunction[CliAction, RIO[Environment, Unit]]] =
    node match {
      case CommandLeaf(definition) =>
        definition match {
          case runnable: RunnableCommand[_] => List(runnable.dispatch)
          case argumentAware: ArgumentAwareCommand[_] => List(argumentAware.dispatchWithArguments(arguments))
          case unsupported => throw new MatchError(unsupported)
        }
      case CommandBranch(_, children) =>
        val handlers = List.newBuilder[PartialFunction[CliAction, RIO[Environment, Unit]]]
        children.foreach(child => handlers ++= executionHandlers(arguments, child))
        handlers.result()
    }
}
