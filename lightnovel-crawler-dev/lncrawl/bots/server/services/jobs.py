from typing import Any, List, Optional

from pydantic import HttpUrl
from sqlmodel import and_, asc, desc, func, select

from ..context import ServerContext
from ..exceptions import AppErrors
from ..models.enums import JobPriority, JobStatus, RunState
from ..models.job import Job, JobDetail
from ..models.novel import Artifact, Novel
from ..models.pagination import Paginated
from ..models.user import User, UserRole
from .tier import JOB_PRIORITY_LEVEL


class JobService:
    def __init__(self, ctx: ServerContext) -> None:
        self._ctx = ctx
        self._db = ctx.db

    def list(
        self,
        offset: int = 0,
        limit: int = 20,
        sort_by: str = "created_at",
        order: str = "desc",
        user_id: Optional[str] = None,
        novel_id: Optional[str] = None,
        priority: Optional[JobPriority] = None,
        status: Optional[JobStatus] = None,
        run_state: Optional[RunState] = None,
    ) -> Paginated[Job]:
        with self._db.session() as sess:
            stmt = select(Job)
            cnt = select(func.count()).select_from(Job)

            # Apply filters
            conditions: List[Any] = []
            if user_id is not None:
                conditions.append(Job.user_id == user_id)
            if novel_id is not None:
                conditions.append(Job.novel_id == novel_id)
            if status is not None:
                conditions.append(Job.status == status)
            if run_state is not None:
                conditions.append(Job.run_state == run_state)
            if priority is not None:
                conditions.append(Job.priority == priority)

            if conditions:
                cnd = and_(*conditions)
                stmt = stmt.where(cnd)
                cnt = cnt.where(cnd)

            # Apply sorting
            sort_column = getattr(Job, sort_by, None)
            if sort_column is None:
                raise AppErrors.sort_column_is_none
            order_fn = desc if order == "desc" else asc
            stmt = stmt.order_by(order_fn(sort_column))

            # Apply pagination
            stmt = stmt.offset(offset).limit(limit)

            total = sess.exec(cnt).one()
            items = sess.exec(stmt).all()

            return Paginated(
                total=total,
                offset=offset,
                limit=limit,
                items=list(items),
            )

    async def create(self, url: HttpUrl, user: User):
        with self._db.session() as sess:
            # get or create novel
            novel_url = url.encoded_string()
            novel = sess.exec(select(Novel).where(Novel.url == novel_url)).first()
            if not novel:
                novel = Novel(url=novel_url)
                sess.add(novel)
                sess.commit()

            # create the job
            job = Job(
                user_id=user.id,
                novel_id=novel.id,
                url=novel.url,
                priority=JOB_PRIORITY_LEVEL[user.tier],
            )
            sess.add(job)
            sess.commit()
            sess.refresh(job)
            return job

    def delete(self, job_id: str, user: User) -> bool:
        with self._db.session() as sess:
            job = sess.get(Job, job_id)
            if not job:
                return True
            if job.user_id != user.id and user.role != UserRole.ADMIN:
                raise AppErrors.forbidden
            sess.delete(job)
            sess.commit()
            return True

    def cancel(self, job_id: str, user: User) -> bool:
        with self._db.session() as sess:
            job = sess.get(Job, job_id)
            if not job or job.status == JobStatus.COMPLETED:
                return True
            if job.user_id != user.id and user.role != UserRole.ADMIN:
                raise AppErrors.forbidden
            who = 'user' if job.user_id == user.id else 'admin'
            job.error = f'Canceled by {who}'
            job.status = JobStatus.COMPLETED
            job.run_state = RunState.CANCELED
            sess.add(job)
            sess.commit()
            return True

    def get(self, job_id: str) -> JobDetail:
        with self._db.session() as sess:
            job = sess.get(Job, job_id)
            if not job:
                raise AppErrors.no_such_job
            user = sess.get_one(User, job.user_id)
            novel = sess.get(Novel, job.novel_id)
            artifacts = sess.exec(
                select(Artifact).where(Artifact.job_id == job.id)
            ).all()
            return JobDetail(
                job=job,
                user=user,
                novel=novel,
                artifacts=list(artifacts),
            )

    def get_artifacts(self, job_id: str) -> List[Artifact]:
        with self._db.session() as sess:
            q = select(Artifact).where(Artifact.job_id == job_id)
            return list(sess.exec(q).all())

    def get_novel(self, job_id: str) -> Novel:
        with self._db.session() as sess:
            job = sess.get(Job, job_id)
            if not job:
                raise AppErrors.no_such_job
            novel = sess.get(Novel, job.novel_id)
            if not novel:
                raise AppErrors.no_such_novel
            return novel
