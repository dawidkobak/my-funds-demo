package dawidkobak.myfunds.cli.model

import dawidkobak.myfunds.cli.CliApplication.Environment
import zio.{Chunk, RIO}
import zio.cli.Command

trait CliAction extends Product with Serializable

trait CommandDefinition[T <: CliAction] {
  def syntax: Command[T]
  def dispatch: PartialFunction[CliAction, RIO[Environment, Unit]]
}

trait RunnableCommand[T <: CliAction] extends CommandDefinition[T]

trait ArgumentAwareCommand[T <: CliAction] extends CommandDefinition[T] {
  def dispatchWithArguments(arguments: Chunk[String]): PartialFunction[CliAction, RIO[Environment, Unit]]

  override def dispatch: PartialFunction[CliAction, RIO[Environment, Unit]] = {
    case action => dispatchWithArguments(Chunk.empty).apply(action)
  }
}
