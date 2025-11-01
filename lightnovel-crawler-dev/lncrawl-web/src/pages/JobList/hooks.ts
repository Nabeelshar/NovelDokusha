import { Auth } from '@/store/_auth';
import { JobStatus, type Job, type PaginatiedResponse } from '@/types';
import { stringifyError } from '@/utils/errors';
import axios from 'axios';
import { debounce } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSelector } from 'react-redux';
import { useSearchParams } from 'react-router-dom';
import { JobStatusFilterParams } from './constants';

interface SearchParams {
  page?: number;
  status?: string;
}

export function useJobList(autoRefresh: boolean = true, customUserId?: string) {
  const isAdmin = useSelector(Auth.select.isAdmin);
  const currentUser = useSelector(Auth.select.user);
  const [searchParams, setSearchParams] = useSearchParams();

  const [refreshId, setRefreshId] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  const [total, setTotal] = useState(0);
  const [jobs, setJobs] = useState<Job[]>([]);

  const perPage = 10;
  const currentPage = useMemo(
    () => parseInt(searchParams.get('page') || '1', 10),
    [searchParams]
  );
  const status: SearchParams['status'] = useMemo(() => {
    const param = searchParams.get('status')?.toLowerCase();
    for (const item of JobStatusFilterParams) {
      if (item.value === param) {
        return param;
      }
    }
  }, [searchParams]);

  const fetchJobs = async (
    page: number,
    limit: number,
    userId?: string,
    status?: SearchParams['status']
  ) => {
    setError(undefined);
    try {
      const offset = (page - 1) * limit;
      const statusParams = JobStatusFilterParams.find(
        (v) => v.value === status
      )?.params;
      const { data } = await axios.get<PaginatiedResponse<Job>>('/api/jobs', {
        params: {
          offset,
          limit,
          user_id: userId,
          ...statusParams,
        },
      });
      setTotal(data.total);
      setJobs(data.items);
    } catch (err: any) {
      setError(stringifyError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const tid = setTimeout(() => {
      const userId = isAdmin ? customUserId : currentUser?.id;
      fetchJobs(currentPage, perPage, userId, status);
    }, 50);
    return () => clearTimeout(tid);
  }, [
    customUserId,
    currentUser?.id,
    isAdmin,
    currentPage,
    status,
    perPage,
    refreshId,
  ]);

  const hasIncompleteJobs = useMemo(() => {
    if (error) return false;
    if (status && status !== JobStatus.PENDING) {
      return false;
    }
    for (const job of jobs) {
      if (job.status != JobStatus.COMPLETED) {
        return true;
      }
    }
    return false;
  }, [error, status, jobs]);

  useEffect(() => {
    if (!autoRefresh) return;
    const interval = hasIncompleteJobs ? 5000 : 15000;
    if (currentPage === 1) {
      const iid = setInterval(() => {
        setRefreshId((v) => v + 1);
      }, interval);
      return () => clearInterval(iid);
    }
  }, [autoRefresh, currentPage, hasIncompleteJobs]);

  const refresh = useCallback(() => {
    setLoading(true);
    setRefreshId((v) => v + 1);
  }, []);

  const updateParams: (updates: SearchParams) => any = useMemo(() => {
    return debounce((updates: SearchParams) => {
      setLoading(true);
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        if (updates.page && updates.page !== 1) {
          next.set('page', String(updates.page));
        } else if (typeof updates.page !== 'undefined') {
          next.delete('page');
        }
        if (
          updates.status &&
          JobStatusFilterParams.findIndex((v) => v.value === updates.status) > 0
        ) {
          next.set('status', updates.status);
        } else if (typeof updates.status !== 'undefined') {
          next.delete('status');
        }
        return next;
      });
    }, 100);
  }, [setSearchParams]);

  return {
    status,
    perPage,
    currentPage,
    jobs,
    total,
    loading,
    error,
    refresh,
    updateParams,
  };
}

export type JobListHook = ReturnType<typeof useJobList>;
