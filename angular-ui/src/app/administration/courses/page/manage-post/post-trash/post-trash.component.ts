import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {DatePipe, NgForOf, NgIf, SlicePipe} from "@angular/common";
import {ActivatedRoute, NavigationEnd, Router, RouterLink} from "@angular/router";
import {CourseService} from "../../../service/course.service";
import {UserService} from "../../../../../common/auth/user.service";
import {ErrorHandler} from "../../../../../common/error-handler.injectable";
import {Subscription} from "rxjs";
import {Post} from "../../../model/view/post";
import {PaginationUtils} from "../../../../../common/dto/page-wrapper";

@Component({
  selector: 'app-course-trash',
  standalone: true,
  imports: [
    NgForOf,
    NgIf,
    RouterLink,
    SlicePipe,
    DatePipe
  ],
  templateUrl: './post-trash.component.html',
})
export class PostTrashComponent implements OnInit, OnDestroy {

  courseService = inject(CourseService);

  route = inject(ActivatedRoute);
  errorHandler = inject(ErrorHandler);
  router = inject(Router);

  postInTrash?: Post[];
  size!: number;
  number!: number;
  courseId?: number;
  totalElements!: number;
  totalPages!: number;
  paginationUtils?: PaginationUtils;
  navigationSubscription?: Subscription;

  getMessage(key: string, details?: any) {
    const messages: Record<string, string> = {
      confirmDelete: 'Do you really want to delete force this element?',
      confirmRestore: 'Do you really want to restore this element?',
      deleted: 'Post was removed successfully.',
      restored: 'Post was restored successfully.'
    }
    return messages[key];
  }

  ngOnInit(): void {
    this.loadData(0);

    this.navigationSubscription = this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd) {
        this.loadData(0);
      }
    })
  }

  ngOnDestroy(): void {
    this.navigationSubscription!.unsubscribe();
  }

  onPageChange(pageNumber: number): void {
    if (pageNumber >= 0 && pageNumber < this.paginationUtils!.totalPages) {
      this.loadData(pageNumber);
    }
  }

  getPageRange(): number[] {
    return this.paginationUtils?.getPageRange() || [];
  }

  loadData(pageNumber: number): void {
    this.courseId = +this.route.snapshot.params['courseId'];

    this.courseService.getTrashedPosts(pageNumber, this.courseId!)
      .subscribe({
        next: (pageWrapper) => {
          this.postInTrash = pageWrapper.content as Post[];
          this.paginationUtils = new PaginationUtils(pageWrapper.page);
        },
        error: (error) => this.errorHandler.handleServerError(error.error)
      });
  }

  confirmDeleteForce(post: Post) {
    if (confirm(this.getMessage('confirmDelete'))) {
      this.courseService.deletePostForce(post, this.courseId!)
        .subscribe({
          next: () => this.router.navigate(['../'], {
            relativeTo: this.route,
            state: {
              msgSuccess: this.getMessage('deleted')
            }
          }),
          error: (error) => this.errorHandler.handleServerError(error.error)
        });
    }
  }

  confirmRestore(postId: number,courseId:number) {
    if (confirm(this.getMessage('confirmRestore'))) {
      this.courseService.restorePost(courseId,postId)
        .subscribe({
          next: () => this.router.navigate(['../'], {
            relativeTo: this.route,
            state: {
              msgSuccess: this.getMessage('restored')
            }
          }),
          error: (error) => this.errorHandler.handleServerError(error.error)
        });
    }
  }

}
