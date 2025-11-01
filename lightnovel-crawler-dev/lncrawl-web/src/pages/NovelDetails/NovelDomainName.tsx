import { type Novel } from '@/types';
import { Avatar, Space, Typography } from 'antd';
import { useMemo } from 'react';

export const NovelDomainName: React.FC<{ novel: Novel }> = ({ novel }) => {
  const novelUrl = useMemo(() => new URL(novel.url), [novel.url]);

  const faviconLink = useMemo(
    () => novelUrl.origin + '/favicon.ico',
    [novelUrl]
  );

  const domainName = useMemo(
    () => novelUrl.hostname.replace('www.', ''),
    [novelUrl]
  );

  return (
    <Space size="small" style={{ marginLeft: -5 }}>
      <Avatar src={faviconLink} size={24} />
      <Typography.Text type="secondary" style={{ fontSize: '18px' }}>
        {domainName}
      </Typography.Text>
    </Space>
  );
};
