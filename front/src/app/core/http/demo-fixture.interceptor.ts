import {Injectable} from '@angular/core';
import {HttpEvent, HttpHandler, HttpInterceptor, HttpParams, HttpRequest, HttpResponse, HttpErrorResponse} from '@angular/common/http';
import {Observable} from 'rxjs';
import {filter, map, shareReplay} from 'rxjs/operators';
import {environment} from '../../../environments/environment';
import {ExpenseBootstrap, ExpenseItem, ExpenseSearchRequest, ExpenseReportPoint} from '../../../api/expense';

interface DemoFixtureDataset {
  asOfDate: string;
  routes: Record<string, any>;
  snapshots: Record<string, {netWorth: any; bonds: any[]}>;
  expenseItems: ExpenseItem[];
}

@Injectable()
export class DemoFixtureInterceptor implements HttpInterceptor {
  private dataset?: Observable<DemoFixtureDataset>;

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (!environment.staticDemo || !request.url.startsWith('/api/')) {
      return next.handle(request);
    }
    if (!this.dataset) {
      this.dataset = next.handle(request.clone({
        method: 'GET', url: '/assets/demo/dataset.json', body: null, params: new HttpParams(),
      })).pipe(
        filter((event): event is HttpResponse<DemoFixtureDataset> => event instanceof HttpResponse),
        map(event => event.body!),
        shareReplay(1)
      );
    }
    return this.dataset.pipe(map(dataset => {
      const path = request.url.split('?')[0];
      const parameters = request.params;
      let body: any;
      if (request.method === 'GET' && path === '/api/wallet/net-worth') {
        body = this.selectSnapshot(dataset, parameters.get('date') || dataset.asOfDate).netWorth;
      } else if (request.method === 'GET' && path === '/api/bond/position/list') {
        body = this.selectSnapshot(dataset, parameters.get('date') || dataset.asOfDate).bonds;
        if (parameters.has('wallet.id')) body = body.filter((position: any) => position.wallet.id === Number(parameters.get('wallet.id')));
      } else if (request.method === 'GET' && path in dataset.routes) {
        body = dataset.routes[path];
        if (path === '/api/wallet/list' && parameters.has('wallet.type')) body = body.filter((wallet: any) => wallet.walletType === parameters.get('wallet.type'));
        if (path === '/api/transaction/list' && parameters.has('wallet.id')) body = body.filter((transaction: any) => transaction.wallet.id === Number(parameters.get('wallet.id')));
      } else if (request.method === 'POST' && ['/api/expenses/search', '/api/expenses/reports', '/api/expenses/export'].includes(path)) {
        const items = this.selectExpenses(dataset, {...(request.body || {}), ...(path === '/api/expenses/reports' ? {includeExcluded: false} : {})});
        if (path === '/api/expenses/search') {
          const page = Math.max(0, request.body?.page || 0);
          const pageSize = Math.min(200, Math.max(1, request.body?.pageSize || 25));
          body = {items: items.slice(page * pageSize, (page + 1) * pageSize), totalCount: items.length, page, pageSize, totals: this.summarizeCurrencies(items.filter(item => item.countingStatus === 'included'))};
        } else if (path === '/api/expenses/reports') {
          const daily = request.body?.periodGranularity === 'day';
          body = {
            periodGranularity: daily ? 'day' : 'month', totals: this.summarizeCurrencies(items),
            monthly: this.buildReportGroups(items, item => ({month: daily ? item.date || item.month : item.month})).sort((first, second) => first.month!.localeCompare(second.month!)),
            categories: this.buildReportGroups(items, item => ({name: item.category?.name || 'Bez kategorii', color: item.category?.color || '#64748B', icon: item.category?.icon || 'tag'})),
            paymentSources: this.buildReportGroups(items, item => ({name: item.paymentSource.name})),
            tags: this.buildReportGroups(items.flatMap(item => item.tags.length ? item.tags.map(tag => ({...item, name: tag.name})) : [{...item, name: 'Bez tagów'}]), item => ({name: item.name})),
          };
        } else {
          const configuration = dataset.routes['/api/expenses/bootstrap'] as ExpenseBootstrap;
          const exportedItems = items.map(item => {
            const folder = configuration.folders.find(entry => entry.id === item.folder.id)!;
            const parent = configuration.folders.find(entry => entry.id === folder.parentId);
            return {folder: parent ? `${parent.name}/${folder.name}` : folder.name, source: item.paymentSource.name, category: item.category?.name || null, tags: item.tags.map(tag => tag.name), fingerprint: null, name: item.name, note: item.note, amount: item.amount, currency: item.currency.code, month: item.month, date: item.date, type: item.type, countingStatus: item.countingStatus};
          });
          const csv = request.body?.format === 'csv';
          const content = csv
            ? ['folder,source,category,tags,name,note,amount,currency,month,date,type,countingStatus,fingerprint', ...exportedItems.map(item => [item.folder, item.source, item.category, item.tags.join(';'), item.name, item.note, item.amount, item.currency, item.month, item.date, item.type, item.countingStatus, ''].map(value => `"${String(value ?? '').replace(/"/g, '""')}"`).join(','))].join('\n')
            : JSON.stringify({format: 'my-funds-expenses-v1', exportedAt: `${dataset.asOfDate}T00:00:00Z`, folders: configuration.folders, sources: configuration.paymentSources, categories: configuration.categories, tags: configuration.tags, items: exportedItems}, null, 2);
          body = {fileName: `my-funds-demo-expenses.${csv ? 'csv' : 'json'}`, mediaType: csv ? 'text/csv;charset=utf-8' : 'application/json', contentBase64: btoa(Array.from(new TextEncoder().encode(content), value => String.fromCharCode(value)).join(''))};
        }
      } else {
        throw new HttpErrorResponse({status: 403, statusText: 'Demo jest tylko do odczytu. Pełna edycja działa w lokalnym Dockerze.', url: request.url});
      }
      return new HttpResponse({status: 200, url: request.url, body: structuredClone(body)});
    }));
  }

  private selectSnapshot(dataset: DemoFixtureDataset, date: string): DemoFixtureDataset['snapshots'][string] {
    const key = Object.keys(dataset.snapshots).find(candidate => candidate === date);
    if (!key) throw new HttpErrorResponse({status: 404, statusText: 'Brak danych demo dla tej daty.'});
    return dataset.snapshots[key];
  }

  private selectExpenses(dataset: DemoFixtureDataset, filters: Partial<ExpenseSearchRequest>): ExpenseItem[] {
    const configuration = dataset.routes['/api/expenses/bootstrap'] as ExpenseBootstrap;
    const folderIds = configuration.folders.filter(folder => folder.id === filters.folderId || folder.parentId === filters.folderId).map(folder => folder.id);
    const search = (filters.search || '').trim().toLocaleLowerCase('pl');
    const items = dataset.expenseItems.filter(item =>
      (!filters.folderId || folderIds.includes(item.folder.id)) &&
      (filters.includeExcluded || item.countingStatus === 'included') &&
      (!search || [item.name, item.note || ''].join(' ').toLocaleLowerCase('pl').includes(search)) &&
      (!filters.dateFrom || (item.date || item.month) >= filters.dateFrom) &&
      (!filters.dateTo || (item.date || item.month) <= filters.dateTo) &&
      (!filters.categoryIds?.length || filters.categoryIds.includes(item.category?.id || -1)) &&
      (!filters.tagIds?.length || item.tags.some(tag => filters.tagIds!.includes(tag.id))) &&
      (!filters.paymentSourceIds?.length || filters.paymentSourceIds.includes(item.paymentSource.id)) &&
      (!filters.currencyCodes?.length || filters.currencyCodes.map(code => code.toUpperCase()).includes(item.currency.code))
    );
    const direction = filters.direction === 'asc' ? 1 : -1;
    return items.sort((first, second) => {
      let comparison: number;
      if (filters.sort === 'amount') comparison = first.amount - second.amount;
      else if (filters.sort === 'category') comparison = (first.category?.name || '').localeCompare(second.category?.name || '', 'pl');
      else if (filters.sort === 'source') comparison = first.paymentSource.name.localeCompare(second.paymentSource.name, 'pl');
      else if (filters.sort === 'name') comparison = first.name.localeCompare(second.name, 'pl');
      else comparison = (first.date || first.month).localeCompare(second.date || second.month);
      return comparison * direction || second.id - first.id;
    });
  }

  private summarizeCurrencies(items: ExpenseItem[]): Array<{currency: string; amount: number}> {
    const totals = new Map<string, number>();
    for (const item of items) {
      totals.set(item.currency.code, (totals.get(item.currency.code) || 0) + item.amount);
    }
    return [...totals].sort().map(([currency, amount]) => ({currency, amount: Math.round(amount * 100) / 100}));
  }

  private buildReportGroups(items: ExpenseItem[], fields: (item: ExpenseItem) => Partial<ExpenseReportPoint>): ExpenseReportPoint[] {
    const points = new Map<string, ExpenseReportPoint>();
    for (const item of items) {
      const attributes = fields(item);
      const key = JSON.stringify([attributes.name || attributes.month, item.currency.code]);
      const previous = points.get(key);
      points.set(key, {...attributes, currency: item.currency.code, amount: Math.round(((previous?.amount || 0) + item.amount) * 100) / 100});
    }
    return [...points.values()].sort((first, second) => first.currency.localeCompare(second.currency) || second.amount - first.amount);
  }
}
