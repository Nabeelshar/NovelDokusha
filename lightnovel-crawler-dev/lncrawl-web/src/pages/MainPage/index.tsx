import { Divider } from 'antd';
import { JobListPage } from '../JobList';
import { RequestNovelCard } from '../JobList/RequestNovelCard';

export const MainPage: React.FC<any> = () => {
  return (
    <>
      <RequestNovelCard />
      <Divider />
      <JobListPage />
    </>
  );
};
