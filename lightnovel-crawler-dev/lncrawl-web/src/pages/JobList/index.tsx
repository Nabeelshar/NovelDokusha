import {
  Button,
  Divider,
  Flex,
  List,
  Pagination,
  Result,
  Spin,
  Typography,
} from 'antd';
import { useJobList } from './hooks';
import { JobFilterBox } from './JobFilterBox';
import { JobListItemCard } from './JobListItemCard';

export const JobListPage: React.FC<{
  userId?: string;
  disableFilters?: boolean;
}> = ({ userId, disableFilters }) => {
  const {
    currentPage,
    error,
    loading,
    perPage,
    total,
    jobs,
    status,
    refresh,
    updateParams,
  } = useJobList(true, userId);

  if (loading) {
    return (
      <Flex align="center" justify="center" style={{ height: '100%' }}>
        <Spin size="large" style={{ marginTop: 100 }} />
      </Flex>
    );
  }

  if (error) {
    return (
      <Flex align="center" justify="center" style={{ height: '100%' }}>
        <Result
          status="error"
          title="Failed to load job list"
          subTitle={error}
          extra={[<Button onClick={refresh}>Retry</Button>]}
        />
      </Flex>
    );
  }

  return (
    <>
      <Typography.Title level={2}>🛠 Job List</Typography.Title>
      {!disableFilters && (
        <>
          <Divider size="small" />
          <JobFilterBox status={status} updateParams={updateParams} />
          <Divider size="small" />
        </>
      )}
      <List
        itemLayout="horizontal"
        dataSource={jobs}
        renderItem={(job) => <JobListItemCard job={job} onChange={refresh} />}
      />
      {(jobs.length > 0 || currentPage > 1) && total / perPage > 1 && (
        <Pagination
          current={currentPage}
          total={total}
          pageSize={perPage}
          showSizeChanger={false}
          onChange={(page) => updateParams({ page })}
          style={{ textAlign: 'center', marginTop: 32 }}
        />
      )}
    </>
  );
};
