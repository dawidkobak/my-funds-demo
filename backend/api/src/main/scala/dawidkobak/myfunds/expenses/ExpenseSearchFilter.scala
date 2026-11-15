package dawidkobak.myfunds.expenses

import java.time.LocalDate

import cats.data.NonEmptyList
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*

final case class ExpenseSearchFilter(
  folderId: Option[Int],
  search: String,
  dateFrom: Option[LocalDate],
  dateTo: Option[LocalDate],
  categoryIds: List[Int],
  tagIds: List[Int],
  paymentSourceIds: List[Int],
  currencyCodes: List[String],
  includeExcluded: Boolean,
  sort: String,
  direction: String,
  page: Int,
  pageSize: Int
) {
  def where(ownerId: Int, forceIncluded: Boolean = false): Fragment = {
    val folderCondition = folderId.map { selectedFolderId =>
      fr"""(
        EXISTS (
          SELECT 1
          FROM mf_expense_folder
          JOIN mf_expense_tag
            ON mf_expense_tag.owner_id = mf_expense_folder.owner_id
            AND lower(mf_expense_tag.name) = lower(mf_expense_folder.name)
            AND mf_expense_tag.is_active = true
          JOIN mf_expense_item_tag ON mf_expense_item_tag.tag_id = mf_expense_tag.id
          WHERE mf_expense_folder.id = $selectedFolderId
            AND mf_expense_folder.owner_id = $ownerId
            AND mf_expense_item_tag.expense_item_id = mf_expense_item.id
        )
        OR (
          NOT EXISTS (
            SELECT 1
            FROM mf_expense_folder
            JOIN mf_expense_tag
              ON mf_expense_tag.owner_id = mf_expense_folder.owner_id
              AND lower(mf_expense_tag.name) = lower(mf_expense_folder.name)
              AND mf_expense_tag.is_active = true
            WHERE mf_expense_folder.id = $selectedFolderId
              AND mf_expense_folder.owner_id = $ownerId
          )
          AND (
            mf_expense_item.folder_id = $selectedFolderId
            OR EXISTS (
              SELECT 1 FROM mf_expense_folder
              WHERE mf_expense_folder.id = mf_expense_item.folder_id
                AND mf_expense_folder.parent_id = $selectedFolderId
                AND mf_expense_folder.owner_id = $ownerId
            )
          )
        )
      )"""
    }
    val searchCondition = Option(search.trim).filter(_.nonEmpty).map { selectedSearch =>
      val searchPattern = s"%${selectedSearch.trim}%"
      fr"""(
        lower(mf_expense_item.name) LIKE lower($searchPattern)
        OR lower(COALESCE(mf_expense_item.note, '')) LIKE lower($searchPattern)
      )"""
    }
    val categoryCondition = NonEmptyList.fromList(categoryIds).map { selectedCategoryIds =>
      Fragments.in(fr"mf_expense_item.category_id", selectedCategoryIds)
    }
    val paymentSourceCondition = NonEmptyList.fromList(paymentSourceIds).map { selectedPaymentSourceIds =>
      Fragments.in(fr"mf_expense_item.payment_source_id", selectedPaymentSourceIds)
    }
    val currencyCondition = NonEmptyList.fromList(currencyCodes.map(_.toUpperCase)).map { selectedCurrencyCodes =>
      fr"""EXISTS (
        SELECT 1 FROM mf_currency
        WHERE mf_currency.id = mf_expense_item.currency_id
          AND """ ++ Fragments.in(fr"mf_currency.code", selectedCurrencyCodes) ++ fr")"
    }
    val tagCondition = NonEmptyList.fromList(tagIds).map { selectedTagIds =>
      fr"""EXISTS (
        SELECT 1 FROM mf_expense_item_tag
        WHERE mf_expense_item_tag.expense_item_id = mf_expense_item.id
          AND """ ++ Fragments.in(fr"mf_expense_item_tag.tag_id", selectedTagIds) ++ fr")"
    }
    Fragments.whereAnd(
      fr"mf_expense_item.owner_id = $ownerId",
      (List(
        Option.when(forceIncluded || !includeExcluded)(fr"mf_expense_item.counting_status = 'included'"),
        folderCondition,
        searchCondition,
        dateFrom.map(selectedDateFrom => fr"COALESCE(mf_expense_item.expense_date, mf_expense_item.expense_month) >= $selectedDateFrom"),
        dateTo.map(selectedDateTo => fr"COALESCE(mf_expense_item.expense_date, mf_expense_item.expense_month) <= $selectedDateTo"),
        categoryCondition,
        paymentSourceCondition,
        currencyCondition,
        tagCondition
      ).flatten)*
    )
  }

  def orderBy: Fragment = {
    val sortColumn = sort match {
      case "name"     => "mf_expense_item.name"
      case "amount"   => "mf_expense_item.amount"
      case "category" => "mf_expense_category.name"
      case "source"   => "mf_expense_payment_source.name"
      case _          => "COALESCE(mf_expense_item.expense_date, mf_expense_item.expense_month)"
    }
    val sortDirection = if (direction.equalsIgnoreCase("asc")) "ASC" else "DESC"
    Fragment.const(s"ORDER BY $sortColumn $sortDirection, mf_expense_item.id DESC")
  }
}

object ExpenseSearchFilter {
  def fromJson(payload: io.circe.Json): ExpenseSearchFilter =
    ExpenseSearchFilter(
      folderId = payload.hcursor.get[Int]("folderId").toOption,
      search = payload.hcursor.get[String]("search").getOrElse(""),
      dateFrom = payload.hcursor.get[String]("dateFrom").toOption.filter(_.nonEmpty).map(LocalDate.parse),
      dateTo = payload.hcursor.get[String]("dateTo").toOption.filter(_.nonEmpty).map(LocalDate.parse),
      categoryIds = payload.hcursor.get[List[Int]]("categoryIds").getOrElse(List.empty),
      tagIds = payload.hcursor.get[List[Int]]("tagIds").getOrElse(List.empty),
      paymentSourceIds = payload.hcursor.get[List[Int]]("paymentSourceIds").getOrElse(List.empty),
      currencyCodes = payload.hcursor.get[List[String]]("currencyCodes").getOrElse(List.empty),
      includeExcluded = payload.hcursor.get[Boolean]("includeExcluded").getOrElse(false),
      sort = payload.hcursor.get[String]("sort").getOrElse("date"),
      direction = payload.hcursor.get[String]("direction").getOrElse("desc"),
      page = math.max(0, payload.hcursor.get[Int]("page").getOrElse(0)),
      pageSize = math.min(200, math.max(1, payload.hcursor.get[Int]("pageSize").getOrElse(25)))
    )
}
