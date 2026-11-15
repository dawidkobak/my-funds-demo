package dawidkobak.myfunds.expenses

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.domain.helper.RequestJsonBody.*
import dawidkobak.myfunds.expenses.handler.{ExpenseConfigurationHandler, ExpenseImportHandler, ExpenseItemHandler, ExpenseQueryHandler}
import zio.*
import zio.http.*

object ExpenseModule {
  def httpApp: Routes[DatabaseAccess, Throwable] =
    Routes(
      Method.GET / "api" / "expenses" / "bootstrap" -> handler { (_: Request) =>
        ExpenseQueryHandler.bootstrap("demo").map(response => Response.json(response.noSpaces))
      },
      Method.POST / "api" / "expenses" / "search" -> handler { (request: Request) =>
        request.readJson.flatMap(ExpenseQueryHandler.search(_, "demo")).map(response => Response.json(response.noSpaces))
      },
      Method.POST / "api" / "expenses" / "reports" -> handler { (request: Request) =>
        request.readJson.flatMap(ExpenseQueryHandler.reports(_, "demo")).map(response => Response.json(response.noSpaces))
      },
      Method.POST / "api" / "expenses" / "export" -> handler { (request: Request) =>
        request.readJson.flatMap(ExpenseQueryHandler.exportData(_, "demo")).map(response => Response.json(response.noSpaces))
      },
      Method.POST / "api" / "expenses" / "import" / "preview" -> handler { (request: Request) =>
        request.readJson.flatMap(ExpenseImportHandler.preview(_, "demo")).map(response => Response.json(response.noSpaces))
      },
      Method.POST / "api" / "expenses" / "import" / "commit" -> handler { (request: Request) =>
        request.readJson.flatMap(ExpenseImportHandler.commit(_, "demo")).map(response => Response.json(response.noSpaces))
      },
      Method.POST / "api" / "expenses" / "item" -> handler { (request: Request) =>
        request.readJson.flatMap(ExpenseItemHandler.create(_, "demo")).map(response => Response.json(response.noSpaces))
      },
      Method.PATCH / "api" / "expenses" / "item" / int("itemId") -> handler { (itemId: Int, request: Request) =>
        request.readJson.flatMap(ExpenseItemHandler.update(itemId, _, "demo")).map(response => Response.json(response.noSpaces))
      },
      Method.DELETE / "api" / "expenses" / "item" / int("itemId") -> handler { (itemId: Int, _: Request) =>
        ExpenseItemHandler.delete(itemId, "demo").map(response => Response.json(response.noSpaces))
      },
      Method.POST / "api" / "expenses" / "folder" -> handler { (request: Request) =>
        request.readJson.flatMap(ExpenseConfigurationHandler.createFolder(_, "demo")).map(response => Response.json(response.noSpaces))
      },
      Method.PATCH / "api" / "expenses" / "folder" / int("folderId") -> handler { (folderId: Int, request: Request) =>
        request.readJson.flatMap(ExpenseConfigurationHandler.updateFolder(folderId, _, "demo")).map(response => Response.json(response.noSpaces))
      },
      Method.DELETE / "api" / "expenses" / "folder" / int("folderId") -> handler { (folderId: Int, _: Request) =>
        ExpenseConfigurationHandler.deleteFolder(folderId, "demo").map(response => Response.json(response.noSpaces))
      },
      Method.POST / "api" / "expenses" / "category" -> handler { (request: Request) =>
        request.readJson.flatMap(ExpenseConfigurationHandler.saveCategory(_, "demo")).map(response => Response.json(response.noSpaces))
      },
      Method.POST / "api" / "expenses" / "tag" -> handler { (request: Request) =>
        request.readJson.flatMap(ExpenseConfigurationHandler.saveTag(_, "demo")).map(response => Response.json(response.noSpaces))
      },
      Method.POST / "api" / "expenses" / "payment-source" -> handler { (request: Request) =>
        request.readJson.flatMap(ExpenseConfigurationHandler.savePaymentSource(_, "demo")).map(response => Response.json(response.noSpaces))
      },
      Method.DELETE / "api" / "expenses" / string("configurationType") / int("configurationId") -> handler { (configurationType: String, configurationId: Int, _: Request) =>
        ExpenseConfigurationHandler.deactivate(configurationType, configurationId, "demo").map(response => Response.json(response.noSpaces))
      }
    )
}
