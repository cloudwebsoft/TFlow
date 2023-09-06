<template>
  <BasicDrawer
    v-bind="$attrs"
    @register="registerDrawer"
    showFooter
    :title="getTitle"
    width="85%"
    :maskClosable="false"
    @ok="handleSubmit"
    :cancelText="'关闭'"
    :destroyOnClose="true"
    @close="onClose"
  >
    <div class="w-full">
      <div class="w-full">
        <form id="visualFormRelate" :name="getFormName" :formCode="formCode">
          <Spin :spinning="spinning">
            <!--  eslint-disable-next-line vue/no-v-html -->
            <div v-html="dataRef['rend']"> </div>
            <input id="cws_id" name="cws_id" type="hidden" />
            <input id="helperRelate" value="1" type="hidden" />
          </Spin>
        </form>
      </div>
      <div v-if="dataRef.isHasAttachment && isUpdate != 3">
        <Upload :file-list="fileList" :before-upload="beforeUpload" @remove="handleRemove">
          <a-button class="mt-1.5">
            <UploadOutlined />
            上传文件
          </a-button>
        </Upload>
      </div>
      <BasicTable
        @register="registerTable"
        v-if="dataRef.isHasAttachment && dataRef.id && isAttachmentShow"
      >
        <template #attTitle="{ record, index }">
          <div v-if="record.previewUrl && record.previewUrl.length > 0"
            ><a :href="record.previewUrl" target="_blank">{{ record.name }}</a></div
          >
          <div v-else>{{ record.name }}</div>
        </template>
        <template #action="{ record, index }">
          <TableAction
            :actions="[
              {
                icon: 'ion:download-outline',
                tooltip: '下载',
                onClick: handleDownload.bind(null, record),
                loading: record.isDownloadAtt ? true : false,
              },
              {
                icon: 'ant-design:delete-outlined',
                color: 'error',
                popConfirm: {
                  title: '是否确认删除',
                  confirm: handleDelete.bind(null, record, index),
                },
                ifShow: () => isUpdate != 3,
              },
            ]"
          />
        </template>
      </BasicTable>
    </div>
  </BasicDrawer>
  <WritePadModal @register="registerWritePadModal" @success="handleWritePadCallBack" />
  <LocationMarkModal @register="registerLocationMarkModal" @success="handleLocationMarkCallBack" />
  <SelDeptModal @register="registerSelDeptModal" @success="handleSelDeptCallBack" />
  <SmartModuleSelDrawer
    @register="registerSmartModuleSelDrawer"
    @success="handleSmartModuleSelCallBack"
    @clear="clearSmartModuleSel"
  />
  <SmartModuleShowDrawer @register="registerSmartModuleShowDrawer" @close="initWindowFunc" />
</template>
<script lang="ts">
  import {
    defineComponent,
    ref,
    computed,
    unref,
    onMounted,
    onUnmounted,
    inject,
    nextTick,
  } from 'vue';
  import { BasicDrawer, useDrawerInner, useDrawer } from '/@/components/Drawer';
  import { UploadProps, Upload, Row, Spin } from 'ant-design-vue';
  import { getToken } from '/@/utils/auth';
  import { useModal } from '/@/components/Modal';
  import SmartModuleShowDrawer from './smartModuleShowDrawer.vue';
  import {
    getVisualAddRelatePage,
    getVisualCreateRelate,
    getVisualEditRelatePage,
    getVisualUpdateRelate,
    getVisualShowRelatePage,
    getVisualListAtt,
    getVisualDelAttach,
    getVisualDownload,
    getItemsForListModuleSel,
    getSelBatchForNest,
    getViewJsScript,
  } from '/@/api/module/module';
  import {
    getFlowCreateNestSheetRelated,
    getFlowUpdateNestSheetRelated,
  } from '/@/api/process/process';
  import { getShowImg } from '/@/api/system/system';
  import { bufToUrl } from '/@/utils/file/base64Conver';

  import { useMessage } from '/@/hooks/web/useMessage';
  import {
    removeScript,
    filterJS,
    ajaxPost,
    ajaxGet,
    ajaxGetJS,
    loadImg,
    initFormCtl,
    myConfirm,
    myMsg,
    ajaxPostJson,
  } from '/@/utils/utils';
  import qs from 'qs';
  import { BasicTable, useTable, TableAction } from '/@/components/Table';
  import { BasicColumn } from '/@/components/Table';
  import { downloadByData } from '/@/utils/file/download';
  import { UploadOutlined } from '@ant-design/icons-vue';
  import { dateUtil as dayjs } from '/@/utils/dateUtil';
  import { useUserStore } from '/@/store/modules/user';
  import WritePadModal from '../../processManagement/modules/WritePadModal.vue';
  import LocationMarkModal from '../../processManagement/modules/LocationMarkModal.vue';
  import SelDeptModal from '../../processManagement/modules/SelDeptModal.vue';
  import SmartModuleSelDrawer from './smartModuleSelDrawer.vue';

  export default defineComponent({
    // eslint-disable-next-line vue/component-definition-name-casing
    name: 'smartModuleRelateTableDrawer',
    components: {
      BasicDrawer,
      BasicTable,
      TableAction,
      Upload,
      Row,
      UploadOutlined,
      WritePadModal,
      LocationMarkModal,
      SelDeptModal,
      SmartModuleSelDrawer,
      Spin,
      SmartModuleShowDrawer,
    },
    emits: ['success', 'register', 'close'],
    setup(_, { emit }) {
      const isUpdate = ref(1); //1:新增，2编辑，3查看
      let dataRef = ref<Recordable>({});
      const activeRecord = ref({});
      const dataRecord = ref<any>({});
      const srcId = ref('-src');
      const userStore = useUserStore();
      const formCode = ref('');
      const curFormUtil: any = inject('curFormUtil');
      let isQuote = false;
      const isAttachmentShow = ref(true);
      const spinning = ref(false);

      let flowId = -1;
      const [registerDrawer, { setDrawerProps, closeDrawer, getVisible }] = useDrawerInner(
        async (data) => {
          setDrawerProps({ confirmLoading: false });

          initWindowFunc();

          removeScript(unref(srcId));

          console.log('useDrawerInner data', data);
          flowId = data.record.flowId;

          /* var liveHelper = new LiveValidation(findObjInForm('visualFormRelate', 'helperRelate'));
        LiveValidation.destroyValidate(liveHelper.formObj.fields);
        $('.LV_presence').remove(); */

          dataRecord.value = data.record || {};
          dataRef.value = {};
          isUpdate.value = data.isUpdate;
          setDrawerProps({ showOkBtn: unref(isUpdate) != 3 });
          if (unref(isUpdate) === 1) {
            await getVisualAddRelatePage({
              moduleCode: unref(dataRecord).moduleCode,
              moduleCodeRelated: unref(dataRecord).moduleCodeRelated,
              parentId: unref(dataRecord).parentId,
              pageType: unref(dataRecord).pageType,
              flowId: flowId,
              actionId: unref(dataRecord).actionId,
              cwsFormName: getFormName.value,
            }).then((res) => {
              dataRef.value = res;
              console.log('res=>', res);

              $('#cws_id').val(unref(dataRef).relateFieldValue);

              console.log('初始化计算控件');
              setTimeout(() => {
                // 初始化计算控件
                initCalculator();
              }, 100);
            });
          } else if (unref(isUpdate) === 2) {
            spinning.value = true;
            await getVisualEditRelatePage({
              moduleCode: unref(dataRecord).moduleCode,
              moduleCodeRelated: unref(dataRecord).moduleCodeRelated,
              parentId: unref(dataRecord).parentId,
              flowId: flowId,
              id: unref(dataRecord).id,
              pageType: unref(dataRecord).pageType,
              actionId: unref(dataRecord).actionId,
              cwsFormName: getFormName.value,
            })
              .then(async (res) => {
                dataRef.value = res;
                isQuote = res.cwsQuoteId != 0;
                console.log('初始化计算控件');
                setTimeout(() => {
                  // 初始化计算控件
                  initCalculator();
                }, 100);
              })
              .finally(() => {
                spinning.value = false;
              });
          } else {
            await getVisualShowRelatePage({
              moduleCode: unref(dataRecord).moduleCode,
              moduleCodeRelated: unref(dataRecord).moduleCodeRelated,
              parentId: unref(dataRecord).parentId,
              id: unref(dataRecord).id,
              cwsFormName: getFormName.value,
            }).then((res) => {
              dataRef.value = res;
            });
          }

          formCode.value = dataRef.value.formCodeRelated;

          console.log('smartModuleRelateTableDrawer getVisible', unref(getVisible));
          await nextTick();
          if (!unref(getVisible)) {
            console.log('smartModuleRelateTableDrawer getVisible is false, now return.');
            return;
          }

          // 1:新增，2编辑，3查看
          let pageType = 'show_relate';
          if (isUpdate.value == 1) {
            pageType = 'add_relate';
          } else if (isUpdate.value == 2) {
            pageType = 'edit_relate';
          }
          console.log('pageType', pageType);

          // 异步获取显示规则脚本
          getViewJsScript({
            moduleCode: unref(dataRecord).moduleCodeRelated,
            pageType: pageType,
            cwsFormName: getFormName.value,
          }).then((res) => {
            console.log('getViewJsScript filterJS');
            filterJS(res.script, '-src', o(getFormName.value), () => {
              return unref(getVisible);
            });
          });

          filterJS(dataRef.value['rend'], '-src', o(getFormName.value), () => {
            return unref(getVisible);
          });

          // isHasAttachment 是否上传文件
          if (unref(dataRef).isHasAttachment && unref(dataRef).id) {
            setTimeout(() => {
              setProps({
                searchInfo: {
                  moduleCode: unref(dataRef).moduleCodeRelated,
                  id: unref(dataRef).id,
                  isShowPage: isUpdate.value != 1 && isUpdate.value != 2,
                  // visitKey: unref(dataRecord).visitKey,
                },
              });
              reloadAttachment();
            }, 10);
          }
          setTimeout(() => {
            // 初始化日期控件
            initDatePicker();

            if (unref(isUpdate) === 1 || unref(isUpdate) === 2) {
              // 设置控件的只读状态
              setNotReadOnly();

              initFormCtl(getFormName.value);
            }

            loadImg('visualFormRelate');
          }, 100);

          let rootPath = import.meta.env.VITE_PUBLIC_PATH;
          if (rootPath.endsWith('/')) {
            rootPath = rootPath.substring(0, rootPath.lastIndexOf('/'));
          }

          const url = `${rootPath}/resource/js/form/form_js_${
            dataRef.value.formCodeRelated
          }.js?pageType=${pageType}&parentId=${dataRecord.value.parentId}&moduleCode=${
            dataRecord.value.moduleCode
          }&moduleCodeRelated=${dataRecord.value.moduleCodeRelated}&id=${
            dataRecord.value.id ? dataRecord.value.id : ''
          }&cwsFormName=${getFormName.value}`;
          console.log('form_js url', pageType + ' ' + url);
          let script1 = document.createElement('script');
          script1.type = 'text/javascript';
          script1.src = url;
          script1.id = `${100}${srcId.value}`;
          document.getElementsByTagName('head')[0].appendChild(script1);

          // 为向下兼容，引入服务端form_js
          try {
            ajaxGetJS(
              `/flow/form_js/form_js_${
                dataRef.value.formCodeRelated
              }.jsp?pageType=${pageType}&parentId=${dataRecord.value.parentId}&moduleCodeRelated=${
                dataRecord.value.moduleCodeRelated
              }&id=${dataRecord.value.id ? dataRecord.value.id : ''}&cwsFormName=${
                getFormName.value
              }`,
            );
          } catch (e) {
            console.log(e);
          }

          // 加载后台事件中配置的前台脚本
          if (dataRef.value.formJs && dataRef.value.formJs.length > 0) {
            let scriptFormJs = document.createElement('script');
            scriptFormJs.type = 'text/javascript';
            scriptFormJs.text = dataRef.value.formJs;
            scriptFormJs.id = `${101}${srcId.value}`;
            document.getElementsByTagName('head')[0].appendChild(scriptFormJs);
          }
        },
      );

      const getTitle = computed(() =>
        unref(isUpdate) === 1 ? '新增' : unref(isUpdate) === 2 ? '编辑' : '查看',
      );

      // -----------------------------------------------------文件上传开始-------------------------------------------------------------

      const fileList = ref<UploadProps['fileList']>([]);
      //上传前校验
      const beforeUpload: UploadProps['beforeUpload'] = (file) => {
        fileList.value = [...fileList.value, file];
        return false;
      };
      // 上传文件删除
      const handleRemove: UploadProps['onRemove'] = (file) => {
        const index = fileList.value.indexOf(file);
        const newFileList = fileList.value.slice();
        newFileList.splice(index, 1);
        fileList.value = newFileList;
      };

      // -----------------------------------------------------文件上传结束-------------------------------------------------------------
      // -----------------------------------------------------文件列表开始-------------------------------------------------------------
      const columns: BasicColumn[] = [
        {
          title: '标题',
          dataIndex: 'name',
          align: 'left',
          slots: { customRender: 'attTitle' },
        },
        {
          title: '创建者',
          dataIndex: 'creatorRealName',
        },
        {
          title: '创建时间',
          dataIndex: 'createDate',
          customRender: ({ text }) => {
            return dayjs(text).format('YYYY-MM-DD');
          },
        },
        {
          title: '大小',
          dataIndex: 'size',
        },
      ];
      const [registerTable, { reload: reloadAttachment, setProps }] = useTable({
        title: '', // '附件列表',
        api: getVisualListAtt,
        columns,
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
        afterFetch: (data) => {
          getFileDataSource(data);
        },
        actionColumn: {
          width: 80,
          title: '操作',
          dataIndex: 'action',
          slots: { customRender: 'action' },
          fixed: undefined,
        },
      });

      const getFileDataSource = (data) => {
        if (data.length == 0) {
          isAttachmentShow.value = false;
        } else {
          isAttachmentShow.value = true;
        }
      };

      //文件列表删除
      function handleDelete(record) {
        let params = {
          moduleCode: unref(dataRef).moduleCodeRelated,
          attachId: record.id,
        };
        getVisualDelAttach(params).then(() => {
          reloadAttachment();
        });
      }

      function handleDownload(record: any) {
        record.isDownloadAtt = true;
        const params = {
          visitKey: record.visitKey,
          attachId: record.id,
          docId: record.docId, // 流程中的附件带有docId
        };
        getVisualDownload(params)
          .then((data) => {
            if (data) {
              downloadByData(data, `${record.name}`);
            }
          })
          .finally(() => {
            record.isDownloadAtt = false;
          });
      }

      // -----------------------------------------------------文件列表结束-------------------------------------------------------------

      const { createMessage } = useMessage();
      async function handleSubmit() {
        try {
          // 要摧毁校验，包括不允许为空的*标以及错误提示都需要摧毁
          console.log(
            "findObjInForm('visualFormRelate', 'helperRelate')",
            findObjInForm(getFormName.value, 'helperRelate'),
          );
          var liveHelper = new LiveValidation(findObjInForm(getFormName.value, 'helperRelate'));
          console.log('relateTableDrawer handleSubmit liveHelper', liveHelper);
          if (!LiveValidation.massValidate(liveHelper.formObj.fields)) {
            LiveValidation.liveErrMsg = LiveValidation.liveErrMsg.replaceAll('<br/>', '\r\n');
            createMessage.warn(LiveValidation.liveErrMsg);
            return;
          }

          setDrawerProps({ confirmLoading: true });
          let form = new FormData(o(getFormName.value) as any);
          if (fileList.value.length > 0) {
            fileList.value.forEach((file: UploadProps['fileList'][number], index) => {
              form.append(`att${index + 1}`, file as any);
            });
          }
          dataRecord.value.formCode = dataRef.value.formCode;
          let query = '?' + qs.stringify(unref(dataRecord)) + '&pageType=add_relate';
          console.log('query', query);
          if (unref(isUpdate) === 1) {
            console.log('flowId', flowId);
            if (flowId == -1) {
              let res = await getFlowCreateNestSheetRelated(form, query);
              if (res.data.res == 1) {
                createMessage.error(res.data.msg);
                return;
              }
              let data = res.data;
              console.log('res', res);
              if (data.isVisual) {
                doVisual(
                  dataRecord.value.moduleCodeRelated,
                  data.formCodeRelated,
                  data.fdaoId,
                  data.tds,
                  data.token,
                );
              } else {
                doFlow(
                  dataRecord.value.moduleCodeRelated,
                  data.formCodeRelated,
                  data.fdaoId,
                  data.tds,
                  data.token,
                  data.sums,
                );
              }
            } else {
              await getVisualCreateRelate(form, query);
            }
          } else if (unref(isUpdate) === 2) {
            console.log('isUpdate', unref(isUpdate), 'flowId', flowId, 'query', query);
            if (flowId == -1) {
              let res = await getFlowUpdateNestSheetRelated(form, query);
              if (res.data.res == 1) {
                createMessage.error(res.data.msg);
                return;
              }

              // 添加主表记录时，编辑已添加的嵌套表的记录，需通过updateRow更新页面上的记录
              if (dataRecord.value.parentId == -1 && res.data.isVisual) {
                let data = res.data;
                // 用于刷新嵌套表格2;
                if (typeof updateRow === 'function') {
                  console.log('updateRow');
                  updateRow(data.formCodeRelated, dataRecord.value.id, data.tds, data.token);
                }
              }

              // 当编辑主表记录，编辑已有的嵌套表的记录时，不需用updateRow刷新了，因为在smartModuleDrawer中调用了reloadNestSheetCtl，如果调用反而会造成在主表中添加新记录，然后添加其中嵌套表的记录，而后再编辑嵌套表记录时，因为updateRow方法在js中存在，而getFlowUpdateNestSheetRelated返回的内容中tds为空，会致调用updateRow后嵌套表各列的内容显示为空。
              // let data = res.data;
              // 用于刷新嵌套表格2
              // if (typeof updateRow === 'function') {
              //   updateRow(data.formCodeRelated, dataRecord.value.id, data.tds, data.token);
              // }

              /* let data = res.data;
              console.log('res', res);
              if (data.isVisual) {
                doVisual(
                  dataRecord.value.moduleCodeRelated,
                  data.formCodeRelated,
                  data.fdaoId,
                  data.tds,
                  data.token,
                );
              } else {
                doFlow(
                  dataRecord.value.moduleCodeRelated,
                  data.formCodeRelated,
                  data.fdaoId,
                  data.tds,
                  data.token,
                  data.sums,
                );
              } */
            } else {
              await getVisualUpdateRelate(form, query);
            }
          }
          handleClose();
          console.log('dataRecord', dataRecord.value);
          emit('success', unref(dataRecord));
          fileList.value = [];
        } finally {
          setDrawerProps({ confirmLoading: false });
        }
      }

      function handleClose() {
        onClose();
        closeDrawer();
      }

      function onClose() {
        console.log('onClose smartModuleRelateTableDrawer');
        curFormUtil.close(getFormName.value);
        emit('close');
        removeScript(unref(srcId));
        // 销毁livevalidation，否则如果点了“确定”验证未通过，再点关闭，然后再进入抽屉时，之前的验证失败的信息仍会弹出
        var liveHelper = new LiveValidation(findObjInForm(getFormName.value, 'helperRelate'));
        LiveValidation.destroyValidate(liveHelper.formObj.fields);
      }

      function getServerUrl() {
        return userStore.getServerUrl;
      }

      function getPublicPath() {
        const publicPath = import.meta.env.VITE_PUBLIC_PATH || '/';
        return publicPath;
      }

      function getCurFormId() {
        return curFormUtil.get();
      }

      const [registerSmartModuleSelDrawer, { openDrawer: openSmartModuleSelDrawer }] = useDrawer();
      let myStart = 1;
      function openSmartModuleSelTableDrawer(selMode = 1, start = 1, params: object) {
        console.log('smart openModuleSelDrawer');
        myStart = start;
        openSmartModuleSelDrawer(true, {
          selMode, // 1 单选，2 多选
          start, // 1 表单域选择 2 嵌套表格拉单
          record: {
            ...params,
          },
        });
      }

      function handleSmartModuleSelCallBack(rows) {
        // if (rows.size() > 1) {
        //    createMessage.warning('请选择一条记录');
        // }
        console.log('流程的抽屉', rows);
        let params: any = getCurModuleFieldListParams();
        console.log('params', params);
        if (myStart == 1) {
          params.id = rows[0].ID;
          params.cwsFormName = getFormName.value;
          let thisBody: Object = document.body;
          // 给表单域控赋值，并带入其映射字段
          getItemsForListModuleSel(params).then(async (res) => {
            console.log(res.data);
            $(thisBody).append(res.data);
            await filterJS(res.data, 'items', o(getFormName.value));

            $('.helper-module-list-sel').remove();
            removeScript('items');
          });
        } else if (myStart == 2) {
          let ids = '';
          for (let k in rows) {
            if (ids == '') {
              ids = rows[k].ID;
            } else {
              ids += ',' + rows[k].ID;
            }
          }
          params.ids = ids;
          // params.flowId = unref(formRowData).flowId;
          getSelBatchForNest(params).then(async (res) => {
            console.log(res);
            await filterJS(
              '<script>' + res.data.script + '<\/script>',
              'items',
              o(getFormName.value),
            );
            if (params.nestType == 'nest_sheet') {
              // 如果编辑时可刷新嵌套表，而添加时不能刷新，因为是通过insertRow_***插入的
              if (unref(isUpdate) === 2) {
                eval('reloadNestSheetCtl' + params.moduleCode + '()');
              }
            } else {
              eval('refreshNestTableCtl' + params.nestFieldName + '()');
            }

            removeScript('items');
          });
        }
      }

      function clearSmartModuleSel() {
        setInputObjValue('', '');
      }

      const [registerSmartModuleShowDrawer, { openDrawer: openSmartModuleShowDrawer }] =
        useDrawer();

      //查看详情
      function openSmartModuleDrawerForShow(moduleCode, id, visitKey) {
        openSmartModuleShowDrawer(true, {
          isUpdate: 3,
          record: {
            moduleCode: moduleCode,
            id: id,
            visitKey: visitKey,
          },
        });
      }

      const getFormName = computed(() => 'visualFormRelate' + curFormUtil?.getFormNo());

      function initWindowFunc() {
        setTimeout(() => {
          curFormUtil?.set(getFormName.value);
        }, 100);

        let newWindow = window as any;
        newWindow.getCurFormId = getCurFormId;
        newWindow.ajaxPost = ajaxPost;
        newWindow.ajaxGet = ajaxGet;
        newWindow.ajaxGetJS = ajaxGetJS;
        newWindow.filterJS = filterJS;
        newWindow.myConfirm = myConfirm;
        newWindow.myMsg = myMsg;
        newWindow.ajaxPostJson = ajaxPostJson;
        newWindow.getServerUrl = getServerUrl;
        newWindow.getPublicPath = getPublicPath;
        newWindow.getToken = getToken;
        newWindow.openWritePadModal = openWritePadModal;
        newWindow.openLocationMarkModal = openLocationMarkModal;
        newWindow.openSelDeptModal = openSelDeptModal;
        newWindow.initFormCtl = initFormCtl;
        newWindow.openSmartModuleSelTableDrawer = openSmartModuleSelTableDrawer;
        newWindow.openSmartModuleDrawerForShow = openSmartModuleDrawerForShow;
      }

      onMounted(() => {
        // initWindowFunc();
      });
      onUnmounted(() => {});

      const [registerWritePadModal, { openModal: openWPadModal }] = useModal();
      const openWritePadModal = (fieldName, w, h) => {
        openWPadModal(true, { fieldName, w, h });
      };

      async function handleWritePadCallBack(record) {}

      const [registerLocationMarkModal, { openModal: openLMModal }] = useModal();
      const openLocationMarkModal = (record) => {
        console.log('openLocationMarkModal record', record);
        openLMModal(true, {
          ...record,
        });
      };

      async function handleLocationMarkCallBack(item) {
        setInputObjValue(item.point.lng + ',' + item.point.lat + ',' + item.address, item.address);
      }

      const [registerSelDeptModal, { openModal: openSDeptModal }] = useModal();
      const openSelDeptModal = (record) => {
        console.log('openSelDeptModal record', record);
        openSDeptModal(true, {
          ...record,
        });
      };

      async function handleSelDeptCallBack(record) {
        let codes = '',
          names = '';
        if (record) {
          if (record.length) {
            for (let i in record) {
              if (codes == '') {
                codes = record[i].value;
                names = record[i].label;
              } else {
                codes += ',' + record[i].value;
                names += ',' + record[i].label;
              }
            }
          } else {
            codes = record.value;
            names = record.label;
          }
        }
        console.log('handleSelDeptCallBack', record, 'record.length', record.length);
        setInputObjValue(codes, names);
      }

      // 设置控件的只读状态
      function setNotReadOnly() {
        let obj = o(getFormName.value);
        for (var i = 0; i < obj.elements.length; i++) {
          let el = obj.elements[i];
          let $el = $(el);

          if ($el.attr('readonly') != null) {
            let isUseReadOnly = true;
            let readOnlyType = $el.attr('readOnlyType');
            if (unref(isUpdate) == 1) {
              if (readOnlyType == '1' || readOnlyType == '2') {
                isUseReadOnly = false;
              }
            } else if (unref(isUpdate) == 2) {
              // 如果是编辑页面
              if (isQuote) {
                // 注意js中存在隐式转换，0=='' 为true
                if (readOnlyType === '0') {
                  isUseReadOnly = false;
                }
              } else {
                // 注意js中存在隐式转换，0=='' 为true
                if (readOnlyType === '0' || readOnlyType === '2') {
                  isUseReadOnly = false;
                }
              }
            }

            console.log(obj.name, 'isUseReadOnly', isUseReadOnly);
            if (!isUseReadOnly) {
              $el.removeAttr('readonly');
              console.log(
                $el.attr('name') + ' ' + $el.attr('title') + ' ' + obj.elements[i].tagName,
              );
              if (el.type == 'radio') {
                // 删除其父节点span的readonly属性
                $el.parent().removeAttr('readonly');
                $el.removeAttr('onchange');
                $el.removeAttr('onfocus');
                $el.click(function () {
                  $(this).attr('checked', true);
                });
              } else if (el.tagName == 'SELECT') {
                $el.removeAttr('onchange');
                $el.removeAttr('onfocus');
              } else if (el.type == 'checkbox') {
                $el.removeAttr('onclick');
              }
            }
          }
        }
      }

      return {
        registerDrawer,
        handleSubmit,
        dataRef,
        activeRecord,
        getTitle,
        onClose,
        registerTable,
        handleDelete,
        handleDownload,
        beforeUpload,
        handleRemove,
        fileList,
        isUpdate,
        formCode,
        registerWritePadModal,
        handleWritePadCallBack,
        registerLocationMarkModal,
        handleLocationMarkCallBack,
        registerSelDeptModal,
        handleSelDeptCallBack,
        registerSmartModuleSelDrawer,
        handleSmartModuleSelCallBack,
        clearSmartModuleSel,
        isAttachmentShow,
        spinning,
        registerSmartModuleShowDrawer,
        initWindowFunc,
        getFormName,
      };
    },
  });
</script>
