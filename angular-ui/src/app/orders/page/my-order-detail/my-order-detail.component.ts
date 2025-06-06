import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute, NavigationEnd, Router, RouterLink} from "@angular/router";
import {Order} from "../../model/order";
import {Payment} from "../../../payment/model/payment";
import {OrdersService} from "../../service/orders.service";
import {PaymentService} from "../../../payment/service/payment.service";
import {Subscription} from "rxjs";
import {ErrorHandler} from "../../../common/error-handler.injectable";
import {DatePipe, NgIf} from "@angular/common";

@Component({
  selector: 'app-my-order-detail',
  standalone: true,
  imports: [
    NgIf,
    DatePipe,
    RouterLink
  ],
  templateUrl: './my-order-detail.component.html',
})
export class MyOrderDetailComponent implements OnInit, OnDestroy{

  route = inject(ActivatedRoute);
  router = inject(Router);
  orderService = inject(OrdersService);
  paymentService = inject(PaymentService);
  errorHandler = inject(ErrorHandler);
  orderId?: string;
  order?: Order;
  payments: Payment[] = [];
  navigationSubscription?: Subscription;

  ngOnInit(): void {
    this.loadData()

    this.navigationSubscription = this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd) {
        this.loadData();
      }
    })

  }

  ngOnDestroy(): void {
    this.navigationSubscription!.unsubscribe();
  }

  loadData() {
    this.orderId = this.route.snapshot.params['orderId'];
    this.orderService.getOrder(this.orderId!)
      .subscribe({
        next: (data) => this.order = data,
        error: (error) => {
          if (error.status === 404) {
            this.errorHandler.handleServerError({
              status: 403,
              message: 'You are not authorized to view this order',
              code: 'NOT_OWNER'
            })
          } else {
            this.errorHandler.handleServerError(error.error)
          }
        }
      })
    this.paymentService.getAllPaymentsByOrder(this.orderId!)
      .subscribe({
        next: payments => this.payments = payments,
        error: (error) => this.errorHandler.handleServerError(error.error)
      })
  }


}
