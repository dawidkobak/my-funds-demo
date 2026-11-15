import {ErrorHandler, Injectable} from '@angular/core';

@Injectable()
export class LazyChunkErrorHandler implements ErrorHandler {
  handleError(error: any): void {
    const chunkLoadFailure = /Loading chunk \d+ failed/;
    if (!chunkLoadFailure.test(error.message)) {
      throw error;
    }

    window.location.reload();
  }
}
