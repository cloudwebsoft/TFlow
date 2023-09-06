<template>
  <BasicModal
    v-bind="$attrs"
    @register="registerModal"
    :title="getTitle"
    @ok="handleSubmit"
    :minHeight="100"
  >
    <Descriptions bordered size="small">
      <DescriptionsItem
        :labelStyle="{ width: '160px' }"
        :contentStyle="{ maxWidth: '300px', minWidth: '200px' }"
        :label="item.label"
        v-for="item in FormSchema"
        :key="item.field"
      >
        <span v-html="item.text"></span>
      </DescriptionsItem>
    </Descriptions>
  </BasicModal>
</template>
<script lang="ts">
  import { defineComponent, ref, unref, reactive, h } from 'vue';
  import { BasicModal, useModalInner } from '/@/components/Modal';
  import { FormSchema } from '/@/components/Table';
  import { Descriptions } from 'ant-design-vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { getQichachaDetail } from '/@/api/module/module';
  import { Item } from 'ant-design-vue/lib/menu';

  export default defineComponent({
    components: {
      BasicModal,
      Descriptions,
      DescriptionsItem: Descriptions.Item,
    },
    emits: ['success', 'register'],
    setup(_, { emit }) {
      const { createMessage, createConfirm } = useMessage();
      let dataRef = reactive<Recordable>({});
      const formData = ref({});

      const [registerModal, { setModalProps, closeModal }] = useModalInner(async (data) => {
        setModalProps({ confirmLoading: false, defaultFullscreen: true, canFullscreen: false });
        dataRef = data.record;
        fetch();
      });
      const FormSchema: FormSchema[] = ref([]);
      FormSchema.value = [
        // {
        //   field: 'KeyNo',
        //   label: '内部KeyNo',
        //   component: 'Input',
        //   required: true,
        //   colProps: {
        //     span: 24,
        //   },
        //   text: '',
        // },
        {
          field: 'Name',
          label: '企业名称',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'No',
          label: '工商注册号',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'BelongOrg',
          label: '登记机关',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'OperName',
          label: '法定代表人名称',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'StartDate',
          label: '成立日期',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'EndDate',
          label: '吊销日期',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'Status',
          label: '登记状态',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'Province',
          label: '省份',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'UpdatedDate',
          label: '更新日期',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'CreditCode',
          label: '统一社会信用代码',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'RegistCapi',
          label: '注册资本',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'EconKind',
          label: '企业类型',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'Address',
          label: '注册地址',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'Scope',
          label: '经营范围',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'TermStart',
          label: '营业期限始',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'TeamEnd',
          label: '营业期限至',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'CheckDate',
          label: '核准日期',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'OrgNo',
          label: '组织机构代码',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'IsOnStock',
          label: '是否上市', //0-未上市，1-上市
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field] == 0 ? '未上市' : form[field] == 1 ? '上市' : '';
          },
        },
        {
          field: 'StockNumber',
          label: '股票代码(如A股和港股同时存在，优先显示A股代码)', //如A股和港股同时存在，优先显示A股代码
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'StockType',
          label: '上市类型', //A股、港股、美股、新三板、新四板
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'OriginalName', //list{Name曾用名,ChangeDate变更日期}
          label: '曾用名',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            let input = '';
            const record = form[field];
            if (record && Array.isArray(record) && record.length > 0) {
              record.forEach((item) => {
                input += `<div>${item.ChangeDate}:${item.Name}</div>`;
              });
            }
            return input;
          },
        },
        {
          field: 'ImageUrl',
          label: '企业Logo地址',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            let input = '';
            const record = form[field];
            if (record) {
              input += `<img style='max-width:50px' src="${record}" alt="企业Logo地址">`;
            }
            return input;
          },
        },
        {
          field: 'EntType',
          label: '企业性质', //企业性质，0-大陆企业，1-社会组织 ，3-中国香港公司，4-事业单位，5-中国台湾公司，6-基金会，7-医院，8-海外公司，9-律师事务所，10-学校 ，11-机关单位，-1-其他

          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return getEntType(form[field]);
          },
        },
        {
          field: 'RecCap',
          label: '实缴资本',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
        {
          field: 'RevokeInfo', //object {CancelDate 注销日期 CancelReason 注销原因 RevokeDate 吊销日期 RevokeReason 吊销原因}
          label: '注销吊销信息',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            let input = '';
            const record = form[field];
            if (record) {
              if (record.CancelDate) {
                input += `<div>注销日期：${record.CancelDate} 注销原因：${
                  record.CancelReason ? record.CancelReason : ''
                }</div>`;
              }
              if (record.RevokeDate) {
                input += `<div>吊销日期：${record.RevokeDate} 吊销原因：${
                  record.RevokeReason ? record.RevokeReason : ''
                }</div>`;
              }
            }
            return input;
          },
        },
        {
          field: 'Area', //object  Province City County
          label: '行政区域',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            let input = '';
            const record = form[field];
            if (record) {
              if (record.Province) {
                input += `<span>${record.Province}</span>`;
              }
              if (record.City) {
                input += `<span>${record.City}</span>`;
              }
              if (record.County) {
                input += `<span>${record.County}</span>`;
              }
            }
            return input;
          },
        },
        {
          field: 'AreaCode',
          label: '行政区划代码',
          component: 'Input',
          required: true,
          colProps: {
            span: 24,
          },
          text: '',
          getText: function (field, form) {
            return form[field];
          },
        },
      ];
      const fetch = () => {
        const params = {
          keyword: unref(dataRef).Name,
        };

        setModalProps({ confirmLoading: true });
        getQichachaDetail(params)
          .then((res) => {
            if (res.res == 0) {
              const result = res.result;
              if (result.Status == 200) {
                formData.value = result.Result || {};
                if (formData.value) {
                  FormSchema.value.forEach((item) => {
                    item.text = item.getText(item.field, formData.value);
                  });
                }
              } else {
                createMessage.warning(res.Message || '请求失败！');
              }
            } else {
              createMessage.warning(res.msg || '请求失败！');
            }
          })
          .finally(() => {
            setModalProps({ confirmLoading: false });
          });
      };
      const getTitle = '查看';

      async function handleSubmit() {
        try {
          createConfirm({
            iconType: 'info',
            title: () => h('span', '提示'),
            content: () => h('span', '您确定要选择吗？'),
            maskClosable: false,
            onOk: async () => {
              setModalProps({ confirmLoading: true });
              const newFormData = {
                Name: unref(formData).Name,
                CreditCode: unref(formData).CreditCode,
                RegistCapi: unref(formData).RegistCapi,
                RecCap: unref(formData).RecCap,
                OperName: unref(formData).OperName,
                OperId: unref(formData).OperId,
                Address: unref(formData).Address,
                StartDate: unref(formData).StartDate,
              };
              emit('success', newFormData);
              closeModal();
            },
          });
        } finally {
          setModalProps({ confirmLoading: false });
        }
      }

      const getEntType = (type) => {
        // 企业性质，0-大陆企业，1-社会组织 ，3-中国香港公司，4-事业单位，5-中国台湾公司，6-基金会，7-医院，8-海外公司，9-律师事务所，10-学校 ，11-机关单位，-1-其他
        let text = '';
        switch (type) {
          case '0':
            text = '大陆企业';
            break;
          case '1':
            text = '社会组织';
            break;
          case '3':
            text = '中国香港公司';
            break;
          case '4':
            text = '事业单位';
            break;
          case '5':
            text = '中国台湾公司';
            break;
          case '6':
            text = '基金会';
            break;
          case '7':
            text = '医院';
            break;
          case '8 ':
            text = '海外公司';
            break;
          case '9':
            text = '律师事务所';
            break;
          case '10':
            text = '学校';
            break;
          case '11':
            text = '机关单位';
            break;
          case '-1':
            text = '其他';
            break;
        }
        return text;
      };
      return {
        registerModal,
        getTitle,
        handleSubmit,
        FormSchema,
      };
    },
  });
</script>
