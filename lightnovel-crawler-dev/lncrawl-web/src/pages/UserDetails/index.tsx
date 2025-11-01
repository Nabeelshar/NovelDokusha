import { Divider, Flex, Result } from 'antd';
import { JobListPage } from '../JobList';
import { UserDetailSection } from './UserDetailSection';
import { useParams } from 'react-router-dom';

export const UserDetailsPage: React.FC<any> = () => {
  const { id } = useParams<{ id: string }>();

  if (!id) {
    return (
      <Flex align="center" justify="center" style={{ height: '100%' }}>
        <Result status="error" title="ID parameter is missing" />
      </Flex>
    );
  }

  return (
    <div style={{ maxWidth: 800, margin: '0 auto' }}>
      <UserDetailSection userId={id} />

      <Divider size="large" />

      <JobListPage userId={id} disableFilters />
    </div>
  );
};
