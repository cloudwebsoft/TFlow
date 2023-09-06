<template>
  <BasicDrawer
    v-bind="$attrs"
    @register="registerDrawer"
    showFooter
    :title="getTitle"
    width="90%"
    :showOkBtn="false"
    :cancelText="'关闭'"
    ref="containerRef"
    :destroyOnClose="true"
    @close="closeCurrentDrawer"
  >
    <div class="p-2">
      <div class="m-auto w-1/2">
        <InputSearch
          v-model:value="keyword"
          placeholder="请输入关键字（如企业名、人名、产品名、地址、电话、经营范围等）"
          enter-button="查询"
          :loading="searchLoading"
          @search="onSearch"
        />
      </div>
      <div class="w-full mt-2">
        <BasicTable @register="registerHandleTable">
          <template #action="{ record }">
            <TableAction
              :actions="[
                {
                  icon: 'clarity:info-standard-line',
                  tooltip: '查看',
                  onClick: handleView.bind(null, record),
                },
                {
                  icon: 'ant-design:save-outlined',
                  tooltip: '确定',
                  onClick: handleSure.bind(null, record),
                  ifShow: isSimple,
                },
              ]"
            />
          </template>
        </BasicTable>
      </div>
      <QichachaSearchDetailModal
        @register="registerQichachaSearchDetailModal"
        @success="handleQichachaSearchDetailModalCallBack"
      />
    </div>
  </BasicDrawer>
</template>
<script lang="ts">
  import { defineComponent, ref, onMounted, h } from 'vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { useModal } from '/@/components/Modal';
  import { BasicDrawer, useDrawerInner } from '/@/components/Drawer';
  import { getQichachaList } from '/@/api/module/module';
  import { Input } from 'ant-design-vue';
  import { BasicTable, useTable, TableAction } from '/@/components/Table';
  import { useDebounceFn } from '@vueuse/core';

  import QichachaSearchDetailModal from './qichachaSearchDetailModal.vue';

  export default defineComponent({
    name: 'ProcessViewDrawer',
    components: {
      BasicDrawer,
      BasicTable,
      InputSearch: Input.Search,
      TableAction,
      QichachaSearchDetailModal,
    },
    emits: ['success', 'register'],
    setup(_, { emit }) {
      const { createMessage, createConfirm } = useMessage();
      let getTitle = '';
      const keyword = ref('');
      const searchLoading = ref(false);
      const isSimple = ref(false);
      //初始化视图抽屉
      const [registerDrawer, { setDrawerProps, closeDrawer }] = useDrawerInner(async (data) => {
        keyword.value = '';
        isSimple.value = data?.isSimple;
        console.log('isSimple', isSimple.value);
        // setTestData();
        setDrawerProps({ confirmLoading: false });
      });
      //关闭抽屉
      function closeCurrentDrawer() {
        emit('success');
      }

      const setTestData = () => {
        const list = [
          {
            KeyNo: '9841a529ad4df8502256e116bd0c030b',
            Name: '广州汇电云联数科能源有限公司',
            CreditCode: '91440101MA59M7F41D',
            StartDate: '2017-04-27',
            OperName: '胡佳',
            Status: '在业',
            No: '440104000696622',
            Address: '广州市黄埔区科学大道18号A栋501房,A栋502房,A栋503房,A栋504房',
          },
          {
            KeyNo: '5bfaceee45790d201b6e60bd4c841903',
            Name: '广州市均能科技有限公司',
            CreditCode: '91440101MA5C4DNY98',
            StartDate: '2018-08-21',
            OperName: '胡佳',
            Status: '在业',
            No: '440106002778776',
            Address: '广州市黄埔区科学大道18号A栋501房,A栋502房,A栋503房,A栋504房',
          },
          {
            KeyNo: '2e733c7ce92d88fc28a59b30d190aa89',
            Name: '广州汇宁时代新能源发展有限公司',
            CreditCode: '91440101MA9Y0NL35X',
            StartDate: '2021-08-02',
            OperName: '谭江浩',
            Status: '在业',
            No: '',
            Address: '广州市黄埔区科学大道18号A栋501房,A栋502房,A栋503房,A栋504房',
          },
          {
            KeyNo: 'd1039e4382783006a95a19885997d478',
            Name: '广州汇电云联新能源有限公司',
            CreditCode: '91440112MACCGM4108',
            StartDate: '2023-03-22',
            OperName: '李志勇',
            Status: '在业',
            No: '440112003952528',
            Address: '广州市黄埔区科学大道18号A栋501房,A栋502房,A栋503房,A栋504房',
          },
          {
            KeyNo: 'ca1bbc4f6f6cd63902690615ce54f5a3',
            Name: '广州市深德装饰工程有限公司',
            CreditCode: '91440300MA5G5B3U23',
            StartDate: '2020-04-21',
            OperName: '杨瑞',
            Status: '在业',
            No: '440300209975339',
            Address: '广州市黄埔区科学大道18号A栋501房,A栋502房,A栋503房,A栋504房',
          },
          {
            KeyNo: '53f250210bbbb19d8a1aee2ade6484c1',
            Name: '无锡汇电均能科技有限公司',
            CreditCode: '91320206MA26F7584P',
            StartDate: '2021-07-05',
            OperName: '谭江浩',
            Status: '存续',
            No: '',
            Address: '无锡惠山经济开发区智慧路18号214-8室',
          },
          {
            KeyNo: 'bd3a05d95c05b90ba342f360f0085d28',
            Name: '湖北聚能云联互联网科技有限公司',
            CreditCode: '91420111MA4L0M8B46',
            StartDate: '2018-09-03',
            OperName: '董海雷',
            Status: '注销',
            No: '',
            Address: '洪山区珞狮路122号荆楚创客咖啡二楼C区037号',
          },
          {
            KeyNo: '0a41a9049689cdc2286f62556308fd0b',
            Name: '广州聚能科技研发有限公司',
            CreditCode: '91440101MA5AUMNN34',
            StartDate: '2018-05-11',
            OperName: '马博',
            Status: '注销',
            No: '',
            Address: '广州市南沙区丰泽东路106号(自编1号楼)X1301-I3950(集群注册)(JM)',
          },
        ];
        setTableData(list);
      };

      const handleColumns: BasicColumn[] = [
        // {
        //   title: 'KeyNo',
        //   dataIndex: 'KeyNo',
        //   align: 'center',
        // },
        {
          title: '企业名称',
          dataIndex: 'Name',
          align: 'center',
        },
        {
          title: '统一社会信用代码',
          dataIndex: 'CreditCode',
          align: 'center',
          width: 180,
        },
        {
          title: '成立日期',
          dataIndex: 'StartDate',
          align: 'center',
          width: 100,
        },
        {
          title: '法定代表人姓名',
          dataIndex: 'OperName',
          align: 'center',
          width: 120,
        },
        {
          title: '状态',
          dataIndex: 'Status',
          align: 'center',
          width: 100,
        },
        {
          title: '注册号',
          dataIndex: 'No',
          align: 'center',
          width: 180,
        },
        {
          title: '注册地址',
          dataIndex: 'Address',
          align: 'center',
        },
      ];
      const [registerHandleTable, { setTableData }] = useTable({
        title: '企业列表',
        api: '',
        columns: handleColumns,
        formConfig: {},
        searchInfo: {}, //额外的参数
        useSearchForm: false,
        showTableSetting: false,
        bordered: true,
        showIndexColumn: true,
        indexColumnProps: { width: 50 },
        immediate: false,
        pagination: false,
        canResize: false,
        actionColumn: {
          width: 100,
          title: '操作',
          dataIndex: 'action',
          slots: { customRender: 'action' },
          fixed: 'right',
        },
      });

      onMounted(() => {});

      const searchData = async (keyword) => {
        searchLoading.value = true;

        const params = { keyword };
        setTimeout(() => {
          searchLoading.value = false;
        }, 2000);
        await getQichachaList(params).then((res) => {
          console.log('res', res);
          if (res.res == 0) {
            const result = res.result || {};
            if (result.Status == 200) {
              if (result.Result && Array.isArray(result.Result) && result.Result.length > 0) {
                setTableData(result.Result);
              }
            } else {
              createMessage.warning(res.Message || '请求失败！');
            }
          } else {
            createMessage.warning(res.msg || '请求失败！');
          }
        });
      };
      // 防抖，延迟500ms执行
      const debounceFresh = useDebounceFn(searchData, 500);
      const onSearch = async (keyword) => {
        if (!keyword) {
          createMessage.warning('请输入关键字');
          return;
        }
        if (keyword && keyword.length < 4) {
          createMessage.warning('请输入至少四个关键字');
          return;
        }
        debounceFresh(keyword);
      };

      const [registerQichachaSearchDetailModal, { openModal }] = useModal();
      //确定
      const handleSure = (record) => {
        createConfirm({
          iconType: 'info',
          title: () => h('span', '提示'),
          content: () => h('span', '您确定要选择吗？'),
          maskClosable: false,
          onOk: async () => {
            handleQichachaSearchDetailModalCallBack(record);
          },
        });
      };
      //查看详情
      const handleView = (record) => {
        openModal(true, {
          record,
        });
      };
      //查看回调
      const handleQichachaSearchDetailModalCallBack = (record) => {
        emit('success', record);
        closeDrawer();
      };
      return {
        registerDrawer,
        getTitle,
        closeCurrentDrawer,
        registerHandleTable,
        onSearch,
        keyword,
        searchLoading,
        handleView,
        handleSure,
        registerQichachaSearchDetailModal,
        handleQichachaSearchDetailModalCallBack,
        isSimple,
      };
    },
  });
</script>
